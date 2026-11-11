package com.example.parkngo.reservation;

import com.example.parkngo.discount.DiscountService;
import com.example.parkngo.facility.FacilityServiceRepository;
import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import com.example.parkngo.feedback.Notification;
import com.example.parkngo.feedback.NotificationService;
import com.example.parkngo.payment.Payment;
import com.example.parkngo.payment.PaymentRepository;
import com.example.parkngo.payment.PaymentService;
import com.example.parkngo.pattern.observer.ParkingEvent;
import com.example.parkngo.pattern.observer.ParkingEventSubject;
import com.example.parkngo.pattern.observer.Observer;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceRepository;
import com.example.parkngo.user.User;
import com.example.parkngo.user.UserRepository;
import com.example.parkngo.vehicle.Vehicle;
import com.example.parkngo.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceMockitoTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ParkingSpaceRepository parkingSpaceRepository;

    @Mock
    private FacilityServiceRepository facilityServiceRepository;

    @Mock
    private DiscountService discountService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ReservationValidator reservationValidator;

    @Mock
    private ParkingFacilityRepository parkingFacilityRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private ReservationService reservationService;

    private ParkingFacility facility;
    private ParkingSpace space;
    private User customer;
    private Vehicle vehicle;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        facility = new ParkingFacility();
        facility.setId(1L);
        facility.setName("City Center Garage");
        facility.setTotalCapacity(50);
        facility.setCurrentOccupancy(5);
        facility.setStatus(ParkingFacility.FacilityStatus.ACTIVE);

        space = new ParkingSpace();
        space.setId(10L);
        space.setSpaceNumber("A-101");
        space.setHourlyRate(BigDecimal.valueOf(100.00));
        space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        space.setFacility(facility);

        customer = new User();
        customer.setId(2L);
        customer.setEmail("customer@test.com");
        customer.setPhone("0771234567");

        vehicle = new Vehicle();
        vehicle.setId(3L);
        vehicle.setRegistrationNumber("ABC-1234");
        vehicle.setCustomer(customer);

        reservation = new Reservation();
        reservation.setId(100L);
        reservation.setCustomer(customer);
        reservation.setVehicle(vehicle);
        reservation.setParkingSpace(space);
        reservation.setArrivalTime(LocalDateTime.now().plusHours(1));
        reservation.setDepartureTime(LocalDateTime.now().plusHours(3));
    }

    @Test
    @DisplayName("createReservation marks space as RESERVED without changing physical occupancy")
    void testCreateReservationUpdatesSpaceAndOccupancy() throws Exception {
        when(parkingSpaceRepository.findById(10L)).thenReturn(Optional.of(space));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation created = reservationService.createReservation(reservation, null, null);

        assertNotNull(created);
        assertEquals(Reservation.ReservationStatus.CONFIRMED, created.getStatus());
        assertEquals(ParkingSpace.ParkingSpaceStatus.RESERVED, space.getStatus());
        assertEquals(5, facility.getCurrentOccupancy());

        verify(parkingSpaceRepository).save(space);
        verify(parkingFacilityRepository, never()).save(facility);
        verify(reservationRepository).save(reservation);
    }

    @Test
    void fullyDiscountedBookingConfirmsWithoutCardDetails() throws Exception {
        var checkout = mock(com.example.parkngo.checkout.CheckoutService.class);
        var quote = new com.example.parkngo.checkout.CheckoutQuote();
        quote.setId(7L); quote.setFinalAmount(BigDecimal.ZERO); quote.setAppliedSubscriptionId(8L);
        var service = new ReservationService(reservationRepository, parkingSpaceRepository, facilityServiceRepository,
                discountService, vehicleRepository, userRepository, notificationService, reservationValidator,
                paymentRepository, paymentService, checkout, null, parkingFacilityRepository);
        when(parkingSpaceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(space));
        when(vehicleRepository.findById(3L)).thenReturn(Optional.of(vehicle));
        when(checkout.previewCheckoutForFacility(eq(2L), any(), isNull(), any(), eq(1L))).thenReturn(quote);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Reservation result = service.bookAndPayReservation(reservation, null, null, "CREDIT_CARD", null, null, null, null, customer);

        assertEquals(Reservation.ReservationStatus.CONFIRMED, result.getStatus());
        assertEquals(0, result.getTotalAmount().signum());
        verify(paymentService, never()).processReservationPayment(any(), any(), any(), any(), any());
        var payment = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertEquals(Payment.PaymentStatus.PAID, payment.getValue().getStatus());
        assertEquals(0, payment.getValue().getAmount().signum());
        verify(checkout).commitCheckout(100L, 7L);
    }

    @Test
    @DisplayName("cancelReservation releases a future space without changing physical occupancy")
    void testCancelReservationFreesSpaceAndDecrementsOccupancy() throws Exception {
        space.setStatus(ParkingSpace.ParkingSpaceStatus.RESERVED);
        facility.setCurrentOccupancy(6);
        reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);

        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reservationService.cancelReservation(100L);

        assertEquals(Reservation.ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals(ParkingSpace.ParkingSpaceStatus.AVAILABLE, space.getStatus());
        assertEquals(6, facility.getCurrentOccupancy());

        verify(parkingSpaceRepository).save(space);
        verify(parkingFacilityRepository, never()).save(facility);
        verify(reservationRepository).save(reservation);
    }

    @Test
    @DisplayName("processCheckOutAndOverstay releases space to AVAILABLE and decrements facility occupancy")
    void testCheckOutFreesSpaceAndDecrementsOccupancy() throws Exception {
        space.setStatus(ParkingSpace.ParkingSpaceStatus.OCCUPIED);
        facility.setCurrentOccupancy(6);
        reservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        reservation.setActualArrivalTime(LocalDateTime.now().minusHours(1));

        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation completed = reservationService.processCheckOutAndOverstay(100L);

        assertEquals(Reservation.ReservationStatus.COMPLETED, completed.getStatus());
        assertEquals(ParkingSpace.ParkingSpaceStatus.AVAILABLE, space.getStatus());
        assertEquals(5, facility.getCurrentOccupancy());

        verify(parkingSpaceRepository).save(space);
        verify(parkingFacilityRepository).save(facility);
        verify(reservationRepository).save(reservation);
    }

    @Test
    @DisplayName("bookAndPayReservation with on-arrival payment sends On-Arrival Payment notification")
    void testBookAndPayReservationWithOnArrivalPayment() throws Exception {
        List<ParkingEvent> publishedEvents = new ArrayList<>();
        Observer testObserver = publishedEvents::add;
        ParkingEventSubject.getInstance().addObserver(testObserver);
        when(parkingSpaceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(space));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment pendingPayment = new Payment();
        pendingPayment.setStatus(Payment.PaymentStatus.PENDING);
        pendingPayment.setPaymentMethod(Payment.PaymentMethod.CASH_ON_ENTRY);
        pendingPayment.setAmount(BigDecimal.valueOf(200.00));
        when(paymentService.processReservationPayment(any(), any(), any(), eq("CASH_ON_ENTRY"), any()))
                .thenReturn(pendingPayment);

        Reservation saved = reservationService.bookAndPayReservation(
                reservation, null, null, "CASH_ON_ENTRY", null, null, null, null, customer
        );

        assertNotNull(saved);
        assertEquals(1, publishedEvents.size());
        ParkingEvent event = publishedEvents.getFirst();
        assertEquals(customer.getId(), event.getRecipientId());
        assertEquals("Booking Confirmed - On-Arrival Payment", event.getTitle());
        assertTrue(event.getMessage().contains("Payment method: On-Arrival Payment"));
        assertEquals(Notification.NotificationType.RESERVATION_CONFIRMED, event.getType());
        assertEquals(saved.getId(), event.getReferenceId());
        ParkingEventSubject.getInstance().removeObserver(testObserver);
        // Verify payment receipt notification is NOT sent since it is not paid yet
        verify(notificationService, never()).sendReservationNotification(
                any(), eq("Payment Received"), any(), any(), any()
        );
    }

    @Test
    @DisplayName("bookAndPayReservation with online payment sends Booking Confirmed & Paid and Payment Received notifications")
    void testBookAndPayReservationWithOnlinePayment() throws Exception {
        when(parkingSpaceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(space));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment paidPayment = new Payment();
        paidPayment.setStatus(Payment.PaymentStatus.PAID);
        paidPayment.setPaymentMethod(Payment.PaymentMethod.CREDIT_CARD);
        paidPayment.setAmount(BigDecimal.valueOf(200.00));
        paidPayment.setTransactionCode("TXN-TEST12345");
        when(paymentService.processReservationPayment(any(), any(), any(), eq("CREDIT_CARD"), any()))
                .thenReturn(paidPayment);

        Reservation saved = reservationService.bookAndPayReservation(
                reservation, null, null, "CREDIT_CARD", "4111111111111111", "Customer Name", "12/28", "123", customer
        );

        assertNotNull(saved);
        // 1. Booking Confirmed & Paid notification
        verify(notificationService).sendReservationNotification(
                eq(customer.getId()),
                eq("Booking Confirmed & Paid"),
                contains("Online payment of LKR"),
                eq(Notification.NotificationType.RESERVATION_CONFIRMED),
                any()
        );
        // 2. Payment Received notification
        verify(notificationService).sendReservationNotification(
                eq(customer.getId()),
                eq("Payment Received"),
                contains("Online payment of LKR"),
                eq(Notification.NotificationType.PAYMENT_SUCCESSFUL),
                any()
        );
    }

    @Test
    @DisplayName("verifyAndCheckIn settles pending cash payment and sends on-site Payment Received notification")
    void testVerifyAndCheckInSettlesOnSitePaymentAndSendsNotification() throws Exception {
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment pendingPayment = new Payment();
        pendingPayment.setId(50L);
        pendingPayment.setReservationId(100L);
        pendingPayment.setStatus(Payment.PaymentStatus.PENDING);
        pendingPayment.setPaymentMethod(Payment.PaymentMethod.CASH_ON_ENTRY);
        pendingPayment.setAmount(BigDecimal.valueOf(300.00));
        when(paymentRepository.findByReservationId(100L)).thenReturn(List.of(pendingPayment));

        reservationService.verifyAndCheckIn(100L);

        assertEquals(Reservation.ReservationStatus.ACTIVE, reservation.getStatus());
        assertEquals(Payment.PaymentStatus.PAID, pendingPayment.getStatus());
        assertEquals("Cash Settled at Gate Entry", pendingPayment.getMaskedCardNumber());

        verify(paymentRepository).save(pendingPayment);
        verify(notificationService).sendReservationNotification(
                eq(customer.getId()),
                eq("Payment Received (On-Site)"),
                contains("On-site payment of LKR 300"),
                eq(Notification.NotificationType.PAYMENT_SUCCESSFUL),
                eq(100L)
        );
    }
}
