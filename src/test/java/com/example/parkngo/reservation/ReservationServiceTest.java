package com.example.parkngo.reservation;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceRepository;
import com.example.parkngo.user.Role;
import com.example.parkngo.user.RoleRepository;
import com.example.parkngo.user.User;
import com.example.parkngo.user.UserRepository;
import com.example.parkngo.vehicle.Vehicle;
import com.example.parkngo.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ReservationServiceTest {
    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ParkingSpaceRepository parkingSpaceRepository;

    @Autowired
    private ParkingFacilityRepository facilityRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private com.example.parkngo.payment.PaymentRepository paymentRepository;

    @Autowired
    private com.example.parkngo.payment.PaymentService paymentService;

    @Autowired
    private com.example.parkngo.facility.FacilityServiceRepository facilityServiceRepository;

    private User testUser;
    private Vehicle testVehicle;
    private ParkingSpace testSpace;
    private ParkingFacility testFacility;

    @BeforeEach
    void setUp() {
        // Reuse or create test customer role
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("ROLE_CUSTOMER");
                    return roleRepository.save(r);
                });

        // Use a unique email per test instance to avoid collision
        String uniqueSuffix = String.valueOf(System.nanoTime());
        testUser = new User();
        testUser.setEmail("anfas.test." + uniqueSuffix + "@gmail.com");
        testUser.setFullName("Anfas Test");
        testUser.setPhone("0771234567");
        testUser.setPassword(new BCryptPasswordEncoder().encode("password"));
        testUser.setRole(customerRole);
        testUser = userRepository.save(testUser);

        // Create test facility (24/7 for baseline tests)
        testFacility = new ParkingFacility();
        testFacility.setName("Test Colombo Facility " + uniqueSuffix);
        testFacility.setAddress("123 Galle Road, Colombo");
        testFacility.setStreet("123 Galle Road");
        testFacility.setCity("Colombo");
        testFacility.setPostalCode("00300");
        testFacility.setContactNumber("+94112345678");
        testFacility.setTotalCapacity(100);
        testFacility.setOpeningTime(LocalTime.of(0, 0));
        testFacility.setClosingTime(LocalTime.of(23, 59));
        testFacility = facilityRepository.save(testFacility);

        // Create test vehicle
        testVehicle = new Vehicle();
        testVehicle.setRegistrationNumber("CAB-" + uniqueSuffix.substring(Math.max(0, uniqueSuffix.length() - 4)));
        testVehicle.setVehicleType("CAR");
        testVehicle.setBrand("Toyota");
        testVehicle.setModel("Aqua");
        testVehicle.setColor("Silver");
        testVehicle.setCustomer(testUser);
        testVehicle = vehicleRepository.save(testVehicle);

        // Create test parking space
        testSpace = new ParkingSpace();
        testSpace.setSpaceNumber("T-" + uniqueSuffix.substring(Math.max(0, uniqueSuffix.length() - 4)));
        testSpace.setFloor(1);
        testSpace.setZone("ZONE-A");
        testSpace.setParkingType("STANDARD");
        testSpace.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        testSpace.setHourlyRate(BigDecimal.valueOf(250.00));
        testSpace.setFacility(testFacility);
        testSpace = parkingSpaceRepository.save(testSpace);
    }

    @Test
    void testCreateReservation() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(LocalDateTime.now().plusHours(1));
        reservation.setDepartureTime(LocalDateTime.now().plusHours(3));

        Reservation created = reservationService.createReservation(reservation);
        assertNotNull(created.getId());
        assertEquals(Reservation.ReservationStatus.CONFIRMED, created.getStatus());
        assertEquals("anfas.test@gmail.com", created.getContactEmail());
        assertEquals("0771234567", created.getContactPhone());
    }

    @Test
    void testAvailabilityCheck() {
        LocalDateTime arrival = LocalDateTime.now().plusHours(1);
        LocalDateTime departure = LocalDateTime.now().plusHours(3);

        assertTrue(reservationService.isSpaceAvailable(testSpace.getId(), arrival, departure));
    }

    @Test
    void testPreventDoubleBookingOnSameSpace() throws Exception {
        LocalDateTime arrival1 = LocalDateTime.now().plusHours(1);
        LocalDateTime departure1 = LocalDateTime.now().plusHours(3);

        Reservation res1 = new Reservation();
        res1.setCustomer(testUser);
        res1.setContactEmail("anfas.test@gmail.com");
        res1.setContactPhone("0771234567");
        res1.setVehicle(testVehicle);
        res1.setParkingSpace(testSpace);
        res1.setArrivalTime(arrival1);
        res1.setDepartureTime(departure1);

        Reservation created1 = reservationService.createReservation(res1);
        assertNotNull(created1.getId());

        // Second vehicle trying to book the exact same space in overlapping time
        Vehicle vehicle2 = new Vehicle();
        vehicle2.setRegistrationNumber("WP-CA-9999");
        vehicle2.setVehicleType("CAR");
        vehicle2.setBrand("Honda");
        vehicle2.setModel("Fit");
        vehicle2.setCustomer(testUser);
        vehicle2 = vehicleRepository.save(vehicle2);

        Reservation res2 = new Reservation();
        res2.setCustomer(testUser);
        res2.setContactEmail("driver2@gmail.com");
        res2.setContactPhone("0719876543");
        res2.setVehicle(vehicle2);
        res2.setParkingSpace(testSpace);
        res2.setArrivalTime(LocalDateTime.now().plusHours(2));
        res2.setDepartureTime(LocalDateTime.now().plusHours(4));

        ReservationValidationException ex = assertThrows(
                ReservationValidationException.class,
                () -> reservationService.createReservation(res2)
        );
        assertTrue(ex.getFieldErrors().containsKey("parkingSpaceId"));
    }

    @Test
    void testPreventDoubleBookingSameVehicleAcrossBays() throws Exception {
        // Create second parking bay
        ParkingSpace testSpace2 = new ParkingSpace();
        testSpace2.setSpaceNumber("B02");
        testSpace2.setFloor(1);
        testSpace2.setZone("ZONE-B");
        testSpace2.setParkingType("STANDARD");
        testSpace2.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        testSpace2.setHourlyRate(BigDecimal.valueOf(250.00));
        testSpace2.setFacility(testFacility);
        testSpace2 = parkingSpaceRepository.save(testSpace2);

        LocalDateTime arrival1 = LocalDateTime.now().plusHours(1);
        LocalDateTime departure1 = LocalDateTime.now().plusHours(3);

        // First booking for testVehicle in Space 1
        Reservation res1 = new Reservation();
        res1.setCustomer(testUser);
        res1.setContactEmail("anfas.test@gmail.com");
        res1.setContactPhone("0771234567");
        res1.setVehicle(testVehicle);
        res1.setParkingSpace(testSpace);
        res1.setArrivalTime(arrival1);
        res1.setDepartureTime(departure1);

        reservationService.createReservation(res1);

        // Same testVehicle attempting to book Space 2 concurrently
        Reservation res2 = new Reservation();
        res2.setCustomer(testUser);
        res2.setContactEmail("anfas.test@gmail.com");
        res2.setContactPhone("0771234567");
        res2.setVehicle(testVehicle);
        res2.setParkingSpace(testSpace2);
        res2.setArrivalTime(LocalDateTime.now().plusHours(2));
        res2.setDepartureTime(LocalDateTime.now().plusHours(4));

        ReservationValidationException ex = assertThrows(
                ReservationValidationException.class,
                () -> reservationService.createReservation(res2)
        );
        assertTrue(ex.getFieldErrors().containsKey("vehicleId"));
    }

    @Test
    void testInvalidEmailValidation() {
        Reservation res = new Reservation();
        res.setCustomer(testUser);
        res.setContactEmail("invalid-email-address");
        res.setContactPhone("0771234567");
        res.setVehicle(testVehicle);
        res.setParkingSpace(testSpace);
        res.setArrivalTime(LocalDateTime.now().plusHours(1));
        res.setDepartureTime(LocalDateTime.now().plusHours(3));

        ReservationValidationException ex = assertThrows(
                ReservationValidationException.class,
                () -> reservationService.createReservation(res)
        );
        assertTrue(ex.getFieldErrors().containsKey("contactEmail"));
    }

    @Test
    void testInvalidPhoneValidation() {
        Reservation res = new Reservation();
        res.setCustomer(testUser);
        res.setContactEmail("anfas.test@gmail.com");
        res.setContactPhone("123"); // Too short
        res.setVehicle(testVehicle);
        res.setParkingSpace(testSpace);
        res.setArrivalTime(LocalDateTime.now().plusHours(1));
        res.setDepartureTime(LocalDateTime.now().plusHours(3));

        ReservationValidationException ex = assertThrows(
                ReservationValidationException.class,
                () -> reservationService.createReservation(res)
        );
        assertTrue(ex.getFieldErrors().containsKey("contactPhone"));
    }

    @Test
    void testPastArrivalTimeValidation() {
        Reservation res = new Reservation();
        res.setCustomer(testUser);
        res.setContactEmail("anfas.test@gmail.com");
        res.setContactPhone("0771234567");
        res.setVehicle(testVehicle);
        res.setParkingSpace(testSpace);
        res.setArrivalTime(LocalDateTime.now().minusHours(2));
        res.setDepartureTime(LocalDateTime.now().plusHours(1));

        ReservationValidationException ex = assertThrows(
                ReservationValidationException.class,
                () -> reservationService.createReservation(res)
        );
        assertTrue(ex.getFieldErrors().containsKey("arrivalTime"));
    }

    @Test
    void testMinimumStayDurationValidation() {
        Reservation res = new Reservation();
        res.setCustomer(testUser);
        res.setContactEmail("anfas.test@gmail.com");
        res.setContactPhone("0771234567");
        res.setVehicle(testVehicle);
        res.setParkingSpace(testSpace);
        LocalDateTime arr = LocalDateTime.now().plusHours(1);
        res.setArrivalTime(arr);
        res.setDepartureTime(arr.plusMinutes(15)); // Less than 30 mins

        ReservationValidationException ex = assertThrows(
                ReservationValidationException.class,
                () -> reservationService.createReservation(res)
        );
        assertTrue(ex.getFieldErrors().containsKey("departureTime"));
    }

    @Test
    void testReservationCancellation() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(LocalDateTime.now().plusHours(1));
        reservation.setDepartureTime(LocalDateTime.now().plusHours(3));

        Reservation created = reservationService.createReservation(reservation);
        reservationService.cancelReservation(created.getId(), testUser);

        Reservation cancelled = reservationService.getReservationById(created.getId());
        assertEquals(Reservation.ReservationStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void testBookAndPayReservation() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(LocalDateTime.now().plusHours(1));
        reservation.setDepartureTime(LocalDateTime.now().plusHours(3));

        Reservation saved = reservationService.bookAndPayReservation(
                reservation,
                null,
                null,
                "CREDIT_CARD",
                "4242424242424242",
                "Anfas Test",
                "12/28",
                "123",
                testUser
        );

        assertNotNull(saved.getId());
        assertEquals(Reservation.ReservationStatus.CONFIRMED, saved.getStatus());
        assertNotNull(saved.getReservationCode());

        var payments = paymentRepository.findByReservationId(saved.getId());
        assertFalse(payments.isEmpty());
        assertEquals(com.example.parkngo.payment.Payment.PaymentStatus.PAID, payments.get(0).getStatus());
    }

    @Test
    void testCancelReservationRefundsPayment() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(LocalDateTime.now().plusHours(2));
        reservation.setDepartureTime(LocalDateTime.now().plusHours(4));

        Reservation saved = reservationService.bookAndPayReservation(
                reservation, null, null, "CREDIT_CARD", "4242424242424242", "Anfas Test", "12/28", "123", testUser
        );

        reservationService.cancelReservation(saved.getId(), testUser);

        Reservation cancelled = reservationService.getReservationById(saved.getId());
        assertEquals(Reservation.ReservationStatus.CANCELLED, cancelled.getStatus());

        var payments = paymentRepository.findByReservationId(saved.getId());
        assertEquals(com.example.parkngo.payment.Payment.PaymentStatus.REFUNDED, payments.get(0).getStatus());
    }

    @Test
    void testUpdateReservationWithVehicleChange() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(LocalDateTime.now().plusHours(1));
        reservation.setDepartureTime(LocalDateTime.now().plusHours(3));

        Reservation saved = reservationService.createReservation(reservation);

        Vehicle vehicle2 = new Vehicle();
        vehicle2.setRegistrationNumber("WP-CB-8888");
        vehicle2.setVehicleType("CAR");
        vehicle2.setBrand("Nissan");
        vehicle2.setModel("Leaf");
        vehicle2.setCustomer(testUser);
        vehicle2 = vehicleRepository.save(vehicle2);

        LocalDateTime newArr = LocalDateTime.now().plusHours(2);
        LocalDateTime newDep = LocalDateTime.now().plusHours(5);

        Reservation updated = reservationService.updateReservationSchedule(
                saved.getId(),
                newArr,
                newDep,
                testSpace.getId(),
                vehicle2.getId(),
                testUser
        );

        assertEquals(newArr, updated.getArrivalTime());
        assertEquals(newDep, updated.getDepartureTime());
        assertEquals(vehicle2.getId(), updated.getVehicle().getId());
    }

    @Test
    void testTurnoverBufferEnforcement() throws Exception {
        LocalDateTime base = LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0);

        Reservation res1 = new Reservation();
        res1.setCustomer(testUser);
        res1.setContactEmail("anfas.test@gmail.com");
        res1.setContactPhone("0771234567");
        res1.setVehicle(testVehicle);
        res1.setParkingSpace(testSpace);
        res1.setArrivalTime(base.plusHours(1));
        res1.setDepartureTime(base.plusHours(2));
        reservationService.createReservation(res1);

        Vehicle vehicle2 = new Vehicle();
        vehicle2.setRegistrationNumber("WP-CA-9999");
        vehicle2.setVehicleType("CAR");
        vehicle2.setBrand("Honda");
        vehicle2.setModel("Fit");
        vehicle2.setCustomer(testUser);
        vehicle2 = vehicleRepository.save(vehicle2);

        // Attempt booking on same space only 5 minutes after res1 departure (violates 15-min buffer)
        Reservation resConflict = new Reservation();
        resConflict.setCustomer(testUser);
        resConflict.setContactEmail("anfas.test@gmail.com");
        resConflict.setContactPhone("0771234567");
        resConflict.setVehicle(vehicle2);
        resConflict.setParkingSpace(testSpace);
        resConflict.setArrivalTime(base.plusHours(2).plusMinutes(5));
        resConflict.setDepartureTime(base.plusHours(3));

        ReservationValidationException ex = assertThrows(
                ReservationValidationException.class,
                () -> reservationService.createReservation(resConflict)
        );
        assertTrue(ex.getMessage().contains("turnover buffer") ||
                (ex.getFieldErrors() != null && ex.getFieldErrors().get("parkingSpaceId").contains("turnover buffer")));

        // Attempt booking starting exactly 15 minutes after res1 departure (respects 15-min buffer) -> succeeds
        Reservation resValid = new Reservation();
        resValid.setCustomer(testUser);
        resValid.setContactEmail("anfas.test@gmail.com");
        resValid.setContactPhone("0771234567");
        resValid.setVehicle(vehicle2);
        resValid.setParkingSpace(testSpace);
        resValid.setArrivalTime(base.plusHours(2).plusMinutes(15));
        resValid.setDepartureTime(base.plusHours(3).plusMinutes(15));

        Reservation savedValid = reservationService.createReservation(resValid);
        assertNotNull(savedValid.getId());
    }

    @Test
    void testEarlyCheckInCutoff() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        // Scheduled arrival is 1 hour in future (outside 15-min early arrival cutoff)
        reservation.setArrivalTime(LocalDateTime.now().plusHours(1));
        reservation.setDepartureTime(LocalDateTime.now().plusHours(3));
        Reservation saved = reservationService.createReservation(reservation);

        ReservationValidationException ex = assertThrows(
                ReservationValidationException.class,
                () -> reservationService.verifyAndCheckIn(saved.getId())
        );
        assertTrue(ex.getMessage().contains("Early check-in not permitted") && ex.getMessage().contains("15 minutes"));
    }

    @Test
    void testEditReservationWithDurationExtensionAndCardPayment() throws Exception {
        LocalDateTime now = LocalDateTime.now().plusDays(2).withMinute(0).withSecond(0).withNano(0);
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(now.plusHours(1));
        reservation.setDepartureTime(now.plusHours(3)); // 2 hours = 500 LKR

        Reservation saved = reservationService.bookAndPayReservation(
                reservation, null, null, "CREDIT_CARD", "4242424242424242", "Anfas Test", "12/28", "123", testUser
        );
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(saved.getTotalAmount()));
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(paymentService.getPaidAmountForReservation(saved.getId())));

        // Extend duration to 4 hours -> 1000 LKR, additional due is 500 LKR
        Reservation updated = reservationService.updateReservationSchedule(
                saved.getId(),
                now.plusHours(1),
                now.plusHours(5), // 4 hours
                testSpace.getId(),
                testVehicle.getId(),
                "CREDIT_CARD",
                "4242424242424242",
                "Anfas Test",
                "12/28",
                "123",
                testUser
        );

        assertEquals(0, BigDecimal.valueOf(1000.00).compareTo(updated.getTotalAmount()));
        assertEquals(0, BigDecimal.valueOf(1000.00).compareTo(paymentService.getPaidAmountForReservation(saved.getId())));
    }

    @Test
    void testEditReservationWithDurationReductionAndCardRefund() throws Exception {
        LocalDateTime now = LocalDateTime.now().plusDays(3).withMinute(0).withSecond(0).withNano(0);
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(now.plusHours(1));
        reservation.setDepartureTime(now.plusHours(5)); // 4 hours = 1000 LKR

        Reservation saved = reservationService.bookAndPayReservation(
                reservation, null, null, "CREDIT_CARD", "4242424242424242", "Anfas Test", "12/28", "123", testUser
        );
        assertEquals(0, BigDecimal.valueOf(1000.00).compareTo(saved.getTotalAmount()));

        // Reduce duration to 2 hours -> 500 LKR, refund due is 500 LKR
        Reservation updated = reservationService.updateReservationSchedule(
                saved.getId(),
                now.plusHours(1),
                now.plusHours(3), // 2 hours
                testSpace.getId(),
                testVehicle.getId(),
                null,
                null,
                null,
                null,
                null,
                testUser
        );

        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(updated.getTotalAmount()));
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(paymentService.getPaidAmountForReservation(saved.getId())));
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(paymentService.getRefundedAmountForReservation(saved.getId())));

        // Check payment records directly
        var payments = paymentRepository.findByReservationId(saved.getId());
        boolean hasCardRefund = payments.stream().anyMatch(p ->
                p.getStatus() == com.example.parkngo.payment.Payment.PaymentStatus.REFUNDED &&
                p.getPaymentMethod() == com.example.parkngo.payment.Payment.PaymentMethod.CREDIT_CARD &&
                p.getAmount().compareTo(BigDecimal.valueOf(500.00)) == 0
        );
        assertTrue(hasCardRefund, "Refund should be recorded with CREDIT_CARD method, not WALLET");
    }

    @Test
    void testEditReservationPayAtGateAndCancel() throws Exception {
        LocalDateTime now = LocalDateTime.now().plusDays(4).withMinute(0).withSecond(0).withNano(0);
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(now.plusHours(1));
        reservation.setDepartureTime(now.plusHours(3)); // 2 hours = 500 LKR

        Reservation saved = reservationService.bookAndPayReservation(
                reservation, null, null, "CASH_ON_ENTRY", null, null, null, null, testUser
        );
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(saved.getTotalAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(paymentService.getPaidAmountForReservation(saved.getId())));

        var payments = paymentRepository.findByReservationId(saved.getId());
        assertEquals(1, payments.size());
        assertEquals(com.example.parkngo.payment.Payment.PaymentStatus.PENDING, payments.get(0).getStatus());
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(payments.get(0).getAmount()));

        // Edit duration to 3 hours (750 LKR) keeping Pay at Gate
        Reservation updated = reservationService.updateReservationSchedule(
                saved.getId(),
                now.plusHours(1),
                now.plusHours(4), // 3 hours
                testSpace.getId(),
                testVehicle.getId(),
                "CASH_ON_ENTRY",
                null,
                null,
                null,
                null,
                testUser
        );

        assertEquals(0, BigDecimal.valueOf(750.00).compareTo(updated.getTotalAmount()));
        var updatedPayments = paymentRepository.findByReservationId(saved.getId());
        assertEquals(com.example.parkngo.payment.Payment.PaymentStatus.PENDING, updatedPayments.get(0).getStatus());
        assertEquals(0, BigDecimal.valueOf(750.00).compareTo(updatedPayments.get(0).getAmount()));

        // Cancel reservation: verify pending payment is marked FAILED/voided with zero charge
        reservationService.cancelReservation(saved.getId(), testUser);
        Reservation cancelled = reservationService.getReservationById(saved.getId());
        assertEquals(Reservation.ReservationStatus.CANCELLED, cancelled.getStatus());

        var finalPayments = paymentRepository.findByReservationId(saved.getId());
        assertEquals(com.example.parkngo.payment.Payment.PaymentStatus.FAILED, finalPayments.get(0).getStatus());
        assertTrue(finalPayments.get(0).getFailureReason().contains("zero charge"));
    }

    @Test
    void testEditReservationAddAndRemoveAddonService() throws Exception {
        com.example.parkngo.facility.FacilityService carWash = new com.example.parkngo.facility.FacilityService();
        carWash.setName("Car Wash " + System.nanoTime());
        carWash.setDescription("Full exterior wash");
        carWash.setPrice(BigDecimal.valueOf(1500.00));
        carWash.setFacility(testFacility);
        carWash.setStatus(com.example.parkngo.facility.FacilityService.ServiceStatus.AVAILABLE);
        carWash = facilityServiceRepository.save(carWash);

        LocalDateTime now = LocalDateTime.now().plusDays(5).withMinute(0).withSecond(0).withNano(0);
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(now.plusHours(1));
        reservation.setDepartureTime(now.plusHours(3)); // 2 hours = 500 LKR base

        // 1. Initial booking with NO services: 500 LKR paid by card
        Reservation saved = reservationService.bookAndPayReservation(
                reservation, null, null, "CREDIT_CARD", "4242424242424242", "Anfas Test", "12/28", "123", testUser
        );
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(saved.getTotalAmount()));
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(paymentService.getPaidAmountForReservation(saved.getId())));
        assertTrue(saved.getServiceItems() == null || saved.getServiceItems().isEmpty());

        // 2. Edit reservation: ADD Car Wash (+1500 LKR). Total becomes 2000 LKR. Additional due = 1500 LKR.
        Reservation updatedWithService = reservationService.updateReservationSchedule(
                saved.getId(),
                now.plusHours(1),
                now.plusHours(3),
                testSpace.getId(),
                testVehicle.getId(),
                List.of(carWash.getId()),
                "CREDIT_CARD",
                "4242424242424242",
                "Anfas Test",
                "12/28",
                "123",
                testUser
        );

        assertEquals(0, BigDecimal.valueOf(2000.00).compareTo(updatedWithService.getTotalAmount()));
        assertEquals(0, BigDecimal.valueOf(1500.00).compareTo(updatedWithService.getServiceAmount()));
        assertEquals(1, updatedWithService.getServiceItems().size());
        assertEquals(carWash.getId(), updatedWithService.getServiceItems().get(0).getService().getId());
        assertEquals(0, BigDecimal.valueOf(2000.00).compareTo(paymentService.getPaidAmountForReservation(saved.getId())));

        // 3. Edit reservation: REMOVE Car Wash (empty list). Total becomes 500 LKR. Refund due = 1500 LKR.
        Reservation updatedWithoutService = reservationService.updateReservationSchedule(
                saved.getId(),
                now.plusHours(1),
                now.plusHours(3),
                testSpace.getId(),
                testVehicle.getId(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                testUser
        );

        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(updatedWithoutService.getTotalAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(updatedWithoutService.getServiceAmount()));
        assertTrue(updatedWithoutService.getServiceItems().isEmpty());
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(paymentService.getPaidAmountForReservation(saved.getId())));
        assertEquals(0, BigDecimal.valueOf(1500.00).compareTo(paymentService.getRefundedAmountForReservation(saved.getId())));
    }

    @Test
    void testBookAndPayReservation_InvalidCardLuhnChecksumFails() {
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(LocalDateTime.now().plusHours(1));
        reservation.setDepartureTime(LocalDateTime.now().plusHours(3));

        // "4242424242424243" fails Luhn checksum
        ReservationValidationException ex = assertThrows(
                ReservationValidationException.class,
                () -> reservationService.bookAndPayReservation(
                        reservation, null, null, "CREDIT_CARD",
                        "4242424242424243", "Anfas Test", "12/28", "123", testUser
                )
        );

        assertTrue(ex.getFieldErrors().containsKey("cardNumber"));
        assertTrue(ex.getMessage().toLowerCase().contains("checksum") || ex.getMessage().toLowerCase().contains("invalid"));
    }

    @Test
    void testBookAndPayReservation_ExpiredCardFails() {
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(LocalDateTime.now().plusHours(1));
        reservation.setDepartureTime(LocalDateTime.now().plusHours(3));

        // Past expiry date "01/20"
        ReservationValidationException ex = assertThrows(
                ReservationValidationException.class,
                () -> reservationService.bookAndPayReservation(
                        reservation, null, null, "CREDIT_CARD",
                        "4242424242424242", "Anfas Test", "01/20", "123", testUser
                )
        );

        assertTrue(ex.getFieldErrors().containsKey("expiryDate"));
    }

    @Test
    void testBookAndPayReservation_InvalidCvcFails() {
        Reservation reservation = new Reservation();
        reservation.setCustomer(testUser);
        reservation.setContactEmail("anfas.test@gmail.com");
        reservation.setContactPhone("0771234567");
        reservation.setVehicle(testVehicle);
        reservation.setParkingSpace(testSpace);
        reservation.setArrivalTime(LocalDateTime.now().plusHours(1));
        reservation.setDepartureTime(LocalDateTime.now().plusHours(3));

        // 2 digits CVC
        ReservationValidationException ex = assertThrows(
                ReservationValidationException.class,
                () -> reservationService.bookAndPayReservation(
                        reservation, null, null, "CREDIT_CARD",
                        "4242424242424242", "Anfas Test", "12/28", "12", testUser
                )
        );

        assertTrue(ex.getFieldErrors().containsKey("cvc"));
    }
}
