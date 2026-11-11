package com.example.parkngo.reservation;

import com.example.parkngo.checkout.CheckoutQuote;
import com.example.parkngo.checkout.CheckoutService;
import com.example.parkngo.discount.Discount;
import com.example.parkngo.discount.DiscountService;
import com.example.parkngo.facility.FacilityService;
import com.example.parkngo.facility.FacilityServiceRepository;
import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import com.example.parkngo.feedback.Notification;
import com.example.parkngo.feedback.NotificationService;
import com.example.parkngo.pattern.observer.ParkingEvent;
import com.example.parkngo.pattern.observer.ParkingEventSubject;
import com.example.parkngo.subscription.Subscription;
import com.example.parkngo.subscription.SubscriptionService;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceRepository;
import com.example.parkngo.user.User;
import com.example.parkngo.user.UserRepository;
import com.example.parkngo.vehicle.Vehicle;
import com.example.parkngo.vehicle.VehicleRepository;
import com.example.parkngo.payment.Payment;
import com.example.parkngo.payment.PaymentRepository;
import com.example.parkngo.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ReservationService {
    private static final BigDecimal WALK_IN_PRICE_MULTIPLIER = new BigDecimal("1.10");
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final ParkingSpaceRepository parkingSpaceRepository;
    private final FacilityServiceRepository facilityServiceRepository;
    private final DiscountService discountService;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ReservationValidator reservationValidator;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final CheckoutService checkoutService;
    private final SubscriptionService subscriptionService;
    private final ParkingFacilityRepository parkingFacilityRepository;

    private static final int PAYMENT_HOLD_MINUTES = 15;
    private static final int NEXT_RESERVATION_PREPARATION_MINUTES = 15;

    public ReservationService(ReservationRepository reservationRepository,
                              ParkingSpaceRepository parkingSpaceRepository,
                              FacilityServiceRepository facilityServiceRepository,
                              DiscountService discountService,
                              VehicleRepository vehicleRepository,
                              UserRepository userRepository,
                              NotificationService notificationService,
                              ReservationValidator reservationValidator) {
        this(reservationRepository, parkingSpaceRepository, facilityServiceRepository, discountService,
                vehicleRepository, userRepository, notificationService, reservationValidator,
                null, null, null, null, null);
    }

    public ReservationService(ReservationRepository reservationRepository,
                              ParkingSpaceRepository parkingSpaceRepository,
                              FacilityServiceRepository facilityServiceRepository,
                              DiscountService discountService,
                              VehicleRepository vehicleRepository,
                              UserRepository userRepository,
                              NotificationService notificationService,
                              ReservationValidator reservationValidator,
                              ParkingFacilityRepository parkingFacilityRepository) {
        this(reservationRepository, parkingSpaceRepository, facilityServiceRepository, discountService,
                vehicleRepository, userRepository, notificationService, reservationValidator,
                null, null, null, null, parkingFacilityRepository);
    }

    public ReservationService(ReservationRepository reservationRepository,
                              ParkingSpaceRepository parkingSpaceRepository,
                              FacilityServiceRepository facilityServiceRepository,
                              DiscountService discountService,
                              VehicleRepository vehicleRepository,
                              UserRepository userRepository,
                              NotificationService notificationService,
                              ReservationValidator reservationValidator,
                              PaymentRepository paymentRepository,
                              PaymentService paymentService,
                              CheckoutService checkoutService,
                              SubscriptionService subscriptionService) {
        this(reservationRepository, parkingSpaceRepository, facilityServiceRepository, discountService,
                vehicleRepository, userRepository, notificationService, reservationValidator,
                paymentRepository, paymentService, checkoutService, subscriptionService, null);
    }

    @Autowired
    public ReservationService(ReservationRepository reservationRepository,
                              ParkingSpaceRepository parkingSpaceRepository,
                              FacilityServiceRepository facilityServiceRepository,
                              DiscountService discountService,
                              VehicleRepository vehicleRepository,
                              UserRepository userRepository,
                              NotificationService notificationService,
                              ReservationValidator reservationValidator,
                              @Autowired(required = false) PaymentRepository paymentRepository,
                              @Autowired(required = false) PaymentService paymentService,
                              @Autowired(required = false) CheckoutService checkoutService,
                              @Autowired(required = false) SubscriptionService subscriptionService,
                              @Autowired(required = false) ParkingFacilityRepository parkingFacilityRepository) {
        this.reservationRepository = reservationRepository;
        this.parkingSpaceRepository = parkingSpaceRepository;
        this.facilityServiceRepository = facilityServiceRepository;
        this.discountService = discountService;
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.reservationValidator = reservationValidator;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.checkoutService = checkoutService;
        this.subscriptionService = subscriptionService;
        this.parkingFacilityRepository = parkingFacilityRepository;
    }

    public List<ParkingSpace> getAvailableSpacesForWindow(Long facilityId, LocalDateTime arrivalTime, LocalDateTime departureTime) {
        if (facilityId == null) {
            return List.of();
        }
        List<ParkingSpace> facilitySpaces = parkingSpaceRepository.findByFacilityId(facilityId);
        if (arrivalTime == null || departureTime == null) {
            return facilitySpaces.stream()
                    .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE)
                .filter(s -> isAllocationAllowed(s, false))
                .toList();
        }

        LocalDateTime bufferArrival = arrivalTime.minusMinutes(ReservationValidator.TURNOVER_BUFFER_MINUTES);
        LocalDateTime bufferDeparture = departureTime.plusMinutes(ReservationValidator.TURNOVER_BUFFER_MINUTES);
        List<Long> bookedSpaceIds = reservationRepository.findBookedSpaceIdsInFacility(facilityId, bufferArrival, bufferDeparture);

        return facilitySpaces.stream()
                .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE)
                .filter(s -> isAllocationAllowed(s, false))
                .filter(s -> !bookedSpaceIds.contains(s.getId()))
                .toList();
    }

    public Reservation bookAndPayReservation(Reservation reservation,
                                            List<Long> serviceIds,
                                            String discountCode,
                                            String paymentMethod,
                                            String cardNumber,
                                            String cardholderName,
                                            String expiryDate,
                                            String cvc,
                                            User customer) throws Exception {
        if (reservation == null) {
            throw new ReservationValidationException("Reservation details are required");
        }

        // Auto-fetch space entity with pessimistic write lock to prevent double booking race conditions
        ParkingSpace space = null;
        Long parkingSpaceId = reservation.getParkingSpace() != null ? reservation.getParkingSpace().getId() : null;
        if (parkingSpaceId != null) {
            space = parkingSpaceRepository.findByIdForUpdate(parkingSpaceId).orElse(null);
            reservation.setParkingSpace(space);
        }

        // Auto-fetch vehicle entity if vehicle ID provided
        Long vehicleId = reservation.getVehicle() != null ? reservation.getVehicle().getId() : null;
        if (vehicleId != null) {
            Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
            if (vehicle != null) {
                reservation.setVehicle(vehicle);
            }
        }

        if (customer != null) {
            reservation.setCustomer(customer);
        }

        // Domain and field validation
        reservationValidator.validateForBooking(reservation, serviceIds, discountCode);

        if (space == null) {
            throw new ReservationValidationException(
                    "Please select a valid parking bay.",
                    "parkingSpaceId",
                    "The selected parking bay could not be found. Please choose another available bay."
            );
        }
        requireAllocationType(space, false);

        // Validate payment inputs
        String method = (paymentMethod != null && !paymentMethod.trim().isEmpty())
                ? paymentMethod.trim().toUpperCase() : "CREDIT_CARD";
        if ("CARD".equals(method)) method = "CREDIT_CARD";
        if ("CASH".equals(method)) method = "CASH_ON_ENTRY";

        // Generate unique reservation code
        if (reservation.getReservationCode() == null || reservation.getReservationCode().isEmpty()) {
            reservation.setReservationCode(generateReservationCode());
        }

        // Calculate duration and base price
        long minutes = Duration.between(reservation.getArrivalTime(), reservation.getDepartureTime()).toMinutes();
        long hours = (minutes + 59) / 60;
        if (hours <= 0) hours = 1;

        BigDecimal baseAmount = space.getHourlyRate().multiply(BigDecimal.valueOf(hours));
        reservation.setBaseAmount(baseAmount);

        // Add extra amenity services if selected
        BigDecimal serviceTotal = BigDecimal.ZERO;
        List<ReservationServiceItem> serviceItems = new ArrayList<>();
        if (serviceIds != null && !serviceIds.isEmpty()) {
            for (Long sId : serviceIds) {
                if (sId == null) continue;
                FacilityService service = facilityServiceRepository.findById(sId).orElse(null);
                if (service != null && service.isAvailable()) {
                    ReservationServiceItem item = new ReservationServiceItem();
                    item.setReservation(reservation);
                    item.setService(service);
                    item.setServicePrice(service.getPrice());
                    serviceItems.add(item);
                    serviceTotal = serviceTotal.add(service.getPrice());
                }
            }
        }
        reservation.setServiceItems(serviceItems);
        reservation.setServiceAmount(serviceTotal);

        // Price checkout & discounts
        BigDecimal preDiscountAmount = baseAmount.add(serviceTotal);
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal total = preDiscountAmount;
        CheckoutQuote quote = null;
        Long customerId = reservation.getCustomer() != null ? reservation.getCustomer().getId() : null;

        if (customerId != null && checkoutService != null) {
            try {
                quote = checkoutService.previewCheckoutForFacility(customerId, preDiscountAmount, discountCode, LocalDateTime.now(), space.getFacility().getId());
                if (quote != null) {
                    total = quote.getFinalAmount().max(BigDecimal.ZERO);
                    discountAmount = preDiscountAmount.subtract(total).max(BigDecimal.ZERO);

                    if (quote.getAppliedSubscriptionId() != null) {
                        reservation.setAppliedSubscriptionId(quote.getAppliedSubscriptionId());
                    } else if (quote.getAppliedDiscountId() != null) {
                        try {
                            reservation.setAppliedDiscount(discountService.getDiscountById(quote.getAppliedDiscountId()));
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                // If checkout preview encounters issues, fall back to promo coupon
                if (discountCode != null && !discountCode.trim().isEmpty()) {
                    try {
                        Discount discount = discountService.getDiscountByCode(discountCode.trim().toUpperCase());
                        if (discount != null && discount.getStatus() == Discount.DiscountStatus.ACTIVE) {
                            reservation.setAppliedDiscount(discount);
                            discountAmount = discountService.calculateDiscount(discount, baseAmount);
                            total = preDiscountAmount.subtract(discountAmount).max(BigDecimal.ZERO);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } else if (discountCode != null && !discountCode.trim().isEmpty()) {
            try {
                Discount discount = discountService.getDiscountByCode(discountCode.trim().toUpperCase());
                if (discount != null && discount.getStatus() == Discount.DiscountStatus.ACTIVE) {
                    reservation.setAppliedDiscount(discount);
                    discountAmount = discountService.calculateDiscount(discount, baseAmount);
                    total = preDiscountAmount.subtract(discountAmount).max(BigDecimal.ZERO);
                }
            } catch (Exception ignored) {}
        }

        if (total.signum() > 0 && ("CREDIT_CARD".equals(method) || "DEBIT_CARD".equals(method))) {
            String cleanNum = cardNumber == null ? "" : cardNumber.replaceAll("[\\s-]", "");
            if (!cleanNum.matches("\\d{13,16}")) {
                throw new ReservationValidationException(
                        "Card number must be 13-16 digits",
                        "cardNumber",
                        "Please enter a valid card number (13-16 digits)."
                );
            }
            if (!PaymentService.isValidLuhn(cleanNum)) {
                throw new ReservationValidationException(
                        "Invalid card number. Checksum verification failed.",
                        "cardNumber",
                        "Invalid card number. Please check for mistyped digits (checksum failed)."
                );
            }
            if (expiryDate == null || !PaymentService.isValidExpiryDate(expiryDate)) {
                throw new ReservationValidationException(
                        "Invalid or expired card date (MM/YY)",
                        "expiryDate",
                        "Please enter a valid, unexpired card date in MM/YY format (e.g. 12/28)."
                );
            }
            if (cvc == null || !PaymentService.isValidCvc(cvc)) {
                throw new ReservationValidationException(
                        "Invalid CVC/CVV security code",
                        "cvc",
                        "Please enter a valid 3 or 4 digit security code (CVC/CVV)."
                );
            }
        }

        reservation.setDiscountAmount(discountAmount);
        reservation.setTotalAmount(total);
        // Do not expose a confirmed reservation until the payment outcome is known.
        // The surrounding transaction rolls the hold back if payment fails.
        reservation.setStatus(Reservation.ReservationStatus.PENDING);
        reservation.setPaymentHoldExpiresAt(LocalDateTime.now().plusMinutes(PAYMENT_HOLD_MINUTES));
        reservation.setReservationType("ONLINE");
        reservation.setIsWalkIn(false);

        for (ReservationServiceItem item : serviceItems) {
            item.setReservation(reservation);
        }

        // Save reservation
        Reservation saved;
        try {
            saved = reservationRepository.save(reservation);
        } catch (Exception e) {
            if (!(reservation instanceof OnlineReservation) && !(reservation instanceof WalkInReservation)) {
                OnlineReservation online = new OnlineReservation();
                copyReservationFields(reservation, online);
                online.setBookingSource("WEB_PORTAL");
                online.setAdvanceBookingRef(reservation.getReservationCode());
                for (ReservationServiceItem item : serviceItems) {
                    item.setReservation(online);
                }
                saved = reservationRepository.save(online);
            } else {
                throw e;
            }
        }

        // Process payment atomically
        Payment processedPayment = null;
        try {
            if (paymentRepository != null || paymentService != null) {
                if (total.compareTo(BigDecimal.ZERO) <= 0) {
                    if (paymentRepository != null) {
                        Payment payment = new Payment();
                        payment.setUser(customer != null ? customer : reservation.getCustomer());
                        payment.setReservationId(saved.getId());
                        payment.setAmount(BigDecimal.ZERO);
                        payment.setPaymentMethod(Payment.PaymentMethod.COUPON);
                        payment.setPaymentType(Payment.PaymentType.RESERVATION);
                        payment.setCardBrand("N/A");
                        payment.setMaskedCardNumber("100% Covered (Pass / Voucher)");
                        payment.setTransactionCode("FREE-" + System.currentTimeMillis() % 1000000);
                        payment.setStatus(Payment.PaymentStatus.PAID);
                        processedPayment = paymentRepository.save(payment);
                    }
                } else if (paymentService != null) {
                    processedPayment = paymentService.processReservationPayment(
                            customer != null ? customer : reservation.getCustomer(),
                            saved.getId(),
                            total,
                            method,
                            cardNumber
                    );
                    if (processedPayment.getStatus() == Payment.PaymentStatus.FAILED) {
                        String reason = processedPayment.getFailureReason() != null ? processedPayment.getFailureReason() : "Payment was declined.";
                        throw new ReservationValidationException(
                                "Payment failed: " + reason,
                                "cardNumber",
                                reason
                        );
                    }
                }
            }
        } catch (ReservationValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Payment processing error during booking: {}", e.getMessage());
            throw new ReservationValidationException("Payment failed: " + e.getMessage(), "cardNumber", e.getMessage());
        }

        if (processedPayment == null) {
            throw new ReservationValidationException("Payment could not be recorded.");
        }
        if (processedPayment.getStatus() == Payment.PaymentStatus.FAILED) {
            throw new ReservationValidationException("Payment failed: " +
                    (processedPayment.getFailureReason() != null ? processedPayment.getFailureReason() : "Payment was declined."),
                    "cardNumber", processedPayment.getFailureReason());
        }
        if (!processedPayment.getStatus().isSettled()
                && processedPayment.getStatus() != Payment.PaymentStatus.PENDING) {
            throw new ReservationValidationException("Payment did not complete.");
        }
        if (processedPayment.getStatus() == Payment.PaymentStatus.PENDING
                && processedPayment.getPaymentMethod() != Payment.PaymentMethod.CASH_ON_ENTRY) {
            throw new ReservationValidationException("Payment is still pending and cannot confirm this reservation.");
        }
        reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);
        reservation.setPaymentHoldExpiresAt(null);
        saved = reservationRepository.save(reservation);

        // Commit checkout hold
        if (quote != null && checkoutService != null) {
            try {
                checkoutService.commitCheckout(saved.getId(), quote.getId());
            } catch (Exception e) {
                log.warn("Failed to commit checkout quote {} for reservation {}: {}", quote.getId(), saved.getId(), e.getMessage());
            }
        }

        // Safe notification call
        try {
            if (notificationService != null && saved.getCustomer() != null) {
                boolean isPayOnArrival = "CASH_ON_ENTRY".equalsIgnoreCase(method)
                        || (processedPayment != null && processedPayment.getStatus() == Payment.PaymentStatus.PENDING);

                String vehiclePlate = (saved.getVehicle() != null && saved.getVehicle().getRegistrationNumber() != null)
                        ? saved.getVehicle().getRegistrationNumber() : "";
                String bayNumber = space != null ? space.getSpaceNumber() : "";

                if (isPayOnArrival) {
                    ParkingEventSubject.getInstance().publish(new ParkingEvent(
                            saved.getCustomer().getId(),
                            "Booking Confirmed - On-Arrival Payment",
                            "Your reservation for vehicle " + vehiclePlate + " has been confirmed for bay " + bayNumber + ". Payment method: On-Arrival Payment (LKR " + total + " will be collected upon arrival at the parking facility).",
                            Notification.NotificationType.RESERVATION_CONFIRMED,
                            saved.getId()
                    ));
                } else {
                    String txnText = (processedPayment != null && processedPayment.getTransactionCode() != null
                            && !processedPayment.getTransactionCode().isEmpty())
                            ? " (Txn: " + processedPayment.getTransactionCode() + ")" : "";
                    notificationService.sendReservationNotification(
                            saved.getCustomer().getId(),
                            "Booking Confirmed & Paid",
                            "Your reservation for vehicle " + vehiclePlate + " has been confirmed for bay " + bayNumber + ". Online payment of LKR " + total + " has been received successfully" + txnText + ".",
                            Notification.NotificationType.RESERVATION_CONFIRMED,
                            saved.getId()
                    );
                    notificationService.sendReservationNotification(
                            saved.getCustomer().getId(),
                            "Payment Received",
                            "Online payment of LKR " + total + " has been received successfully" + txnText + ".",
                            Notification.NotificationType.PAYMENT_SUCCESSFUL,
                            saved.getId()
                    );
                }
            }
        } catch (Exception ignored) {}

        return saved;
    }

    public Reservation createReservation(Reservation reservation) throws Exception {
        return createReservation(reservation, null, null);
    }

    public Reservation createReservation(Reservation reservation, List<Long> serviceIds, String discountCode) throws Exception {
        if (reservation == null) {
            throw new ReservationValidationException("Reservation details are required");
        }

        // Auto-fetch space entity if space ID provided (acquire write lock)
        ParkingSpace space = null;
        Long parkingSpaceId = reservation.getParkingSpace() != null ? reservation.getParkingSpace().getId() : null;
        if (parkingSpaceId != null) {
            space = parkingSpaceRepository.findByIdForUpdate(parkingSpaceId).orElse(null);
            if (space == null) {
                space = parkingSpaceRepository.findById(parkingSpaceId).orElse(null);
            }
            if (space == null) {
                space = reservation.getParkingSpace();
            }
            reservation.setParkingSpace(space);
        } else if (reservation.getParkingSpace() != null) {
            space = reservation.getParkingSpace();
        }

        // Auto-fetch vehicle entity if vehicle ID provided
        Long vehicleId = reservation.getVehicle() != null ? reservation.getVehicle().getId() : null;
        if (vehicleId != null) {
            Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
            if (vehicle != null) {
                reservation.setVehicle(vehicle);
            }
        }

        // Domain and field validation for time, vehicle, bay, discount, and amenities.
        reservationValidator.validateForBooking(reservation, serviceIds, discountCode);

        if (space == null) {
            throw new ReservationValidationException(
                "Please select a valid parking bay.",
                "parkingSpaceId",
                "The selected parking bay could not be found. Please choose another available bay."
            );
        }
        requireAllocationType(space, false);

        // Generate unique reservation code
        if (reservation.getReservationCode() == null || reservation.getReservationCode().isEmpty()) {
            reservation.setReservationCode(generateReservationCode());
        }

        // Calculate duration and base price
        long minutes = Duration.between(reservation.getArrivalTime(), reservation.getDepartureTime()).toMinutes();
        long hours = (minutes + 59) / 60; // round up to next full hour
        if (hours <= 0) hours = 1;

        BigDecimal baseAmount = space.getHourlyRate().multiply(BigDecimal.valueOf(hours));
        reservation.setBaseAmount(baseAmount);

        // Add extra amenity services if selected
        BigDecimal serviceTotal = BigDecimal.ZERO;
        List<ReservationServiceItem> serviceItems = new ArrayList<>();
        if (serviceIds != null && !serviceIds.isEmpty()) {
            for (Long sId : serviceIds) {
                if (sId == null) continue;
                Long serviceId = sId;
                FacilityService service = facilityServiceRepository.findById(serviceId).orElse(null);
                if (service != null && service.isAvailable()) {
                    ReservationServiceItem item = new ReservationServiceItem();
                    item.setReservation(reservation);
                    item.setService(service);
                    item.setServicePrice(service.getPrice());
                    serviceItems.add(item);
                    serviceTotal = serviceTotal.add(service.getPrice());
                }
            }
        }
        reservation.setServiceItems(serviceItems);
        reservation.setServiceAmount(serviceTotal);

        // Price the checkout: an active membership pass beats a discount
        // coupon beats full price, applied
        // via the same hold-then-commit checkout used by package purchases
        // - this is what actually spends a pass visit (or a capped coupon's
        // use) on THIS reservation, rather than just displaying a number.
        BigDecimal preDiscountAmount = baseAmount.add(serviceTotal);
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal total = preDiscountAmount;
        CheckoutQuote quote = null;
        Long customerId = reservation.getCustomer() != null ? reservation.getCustomer().getId() : null;
        if (customerId != null && checkoutService != null) {
            quote = checkoutService.previewCheckoutForFacility(customerId, preDiscountAmount, discountCode, LocalDateTime.now(), space.getFacility().getId());
            total = quote.getFinalAmount().max(BigDecimal.ZERO);
            discountAmount = preDiscountAmount.subtract(total).max(BigDecimal.ZERO);

            if (quote.getAppliedSubscriptionId() != null) {
                reservation.setAppliedSubscriptionId(quote.getAppliedSubscriptionId());
            } else if (quote.getAppliedDiscountId() != null) {
                try {
                    reservation.setAppliedDiscount(discountService.getDiscountById(quote.getAppliedDiscountId()));
                } catch (Exception ignored) {
                    // Display-only lookup - the hold itself already succeeded.
                }
            }
        } else if (discountCode != null && !discountCode.trim().isEmpty()) {
            // No logged-in customer (should not happen for the online booking
            // flow, but kept as a safe fallback): plain discount-code lookup
            // with no membership consideration and no usage tracking.
            try {
                Discount discount = discountService.getDiscountByCode(discountCode.trim().toUpperCase());
                if (discount != null && discount.getStatus() == Discount.DiscountStatus.ACTIVE) {
                    reservation.setAppliedDiscount(discount);
                    discountAmount = discountService.calculateDiscount(discount, baseAmount);
                    total = preDiscountAmount.subtract(discountAmount).max(BigDecimal.ZERO);
                }
            } catch (Exception ignored) {}
        }
        reservation.setDiscountAmount(discountAmount);
        reservation.setTotalAmount(total);

        reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);
        reservation.setPaymentHoldExpiresAt(null);
        if (space != null) {
            space.setStatus(ParkingSpace.ParkingSpaceStatus.RESERVED);
            parkingSpaceRepository.save(space);
        }
        Reservation saved = reservationRepository.save(reservation);

        // Finalize the checkout hold
        if (quote != null && checkoutService != null) {
            try {
                checkoutService.commitCheckout(saved.getId(), quote.getId());
            } catch (Exception e) {
                log.warn("Failed to commit checkout quote {} for reservation {}: {}", quote.getId(), saved.getId(), e.getMessage());
            }
        }

        // Safe notification call (non-blocking / optional)
        try {
            if (notificationService != null && saved.getCustomer() != null) {
                notificationService.sendReservationNotification(
                        saved.getCustomer().getId(),
                        "Booking Confirmed",
                        "Your reservation for vehicle " + (saved.getVehicle() != null ? saved.getVehicle().getRegistrationNumber() : "") + " has been confirmed for bay " + (space != null ? space.getSpaceNumber() : "") + ".",
                        Notification.NotificationType.RESERVATION_CONFIRMED,
                        saved.getId()
                );
            }
        } catch (Exception ignored) {}

        return saved;
    }

    public Reservation createWalkInReservation(Long spaceId, String licensePlate, String vehicleType,
                                               String brand, String modelName, Integer durationHours,
                                               User staffUser) throws Exception {
        return createWalkInReservation(spaceId, licensePlate, vehicleType, brand, modelName, "Unknown", "Guest Driver", null, durationHours, staffUser);
    }

    public Reservation createWalkInReservation(Long spaceId, String licensePlate, String vehicleType,
                                               String brand, String modelName, String color,
                                               String guestName, String contactPhone,
                                               Integer durationHours, User staffUser) throws Exception {
        return createWalkInReservation(spaceId, licensePlate, vehicleType, brand, modelName, color, guestName, contactPhone, null, null, durationHours, staffUser);
    }

    public Reservation createWalkInReservation(Long spaceId, String licensePlate, String vehicleType,
                                               String brand, String modelName, String color,
                                               String guestName, String contactPhone,
                                               LocalDateTime arrivalTime, LocalDateTime departureTime,
                                               Integer durationHours, User staffUser) throws Exception {
        // Lock and re-read the space inside this transaction. The browser's
        // map snapshot is advisory; this is the authoritative availability check.
        ParkingSpace space = (spaceId != null) ? parkingSpaceRepository.findByIdForUpdate(spaceId).orElse(null) : null;

        int hours = (durationHours != null && durationHours > 0) ? durationHours : 2;
        LocalDateTime arr = (arrivalTime != null) ? arrivalTime.withSecond(0).withNano(0) : LocalDateTime.now().withSecond(0).withNano(0);

        // Only auto-derive departure from the duration preset when the staff
        // member didn't explicitly set one themselves. If they did set both
        // times, an illogical pair (departure at or before arrival) is a
        // genuine input mistake and must be rejected with a clear error,
        // not silently overwritten with a guessed time - that previously let
        // a walk-in pass go out with a departure time nothing to do with
        // what the staff member actually typed.
        LocalDateTime dep;
        if (departureTime != null) {
            dep = departureTime.withSecond(0).withNano(0);
            if (!dep.isAfter(arr)) {
                throw new ReservationValidationException(
                        "Scheduled departure time must be after the arrival time.",
                        "departureTime",
                        "Departure must be later than the arrival time.");
            }
            long minutes = java.time.Duration.between(arr, dep).toMinutes();
            hours = (int) Math.max(1, (minutes + 59) / 60);
        } else {
            dep = arr.plusHours(hours);
        }

        // Domain validation for Walk-in
        reservationValidator.validateForWalkIn(space, licensePlate, vehicleType, hours, guestName, contactPhone, staffUser);
        if (space == null) {
            throw new ReservationValidationException("A valid parking space is required for a walk-in reservation");
        }
        // Same server-side time-window/conflict check used by online booking;
        // the row lock above makes the check-and-occupy sequence atomic.
        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
        reservationValidator.validateParkingSpace(space, true, null, arr, dep, errors);
        if (!errors.isEmpty()) {
            throw new ReservationValidationException("Availability conflict.", errors);
        }

        // SQL Server's existing WALK_IN structure stores guest details on
        // WalkInReservation. Do not create a vehicle/customer record merely
        // to satisfy an online-reservation relationship.
        String cleanPlate = com.example.parkngo.util.SriLankanPlateValidator.normalize(licensePlate);

        BigDecimal walkInHourlyRate = space.getHourlyRate().multiply(WALK_IN_PRICE_MULTIPLIER)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal basePrice = walkInHourlyRate.multiply(BigDecimal.valueOf(hours))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        WalkInReservation walkIn = new WalkInReservation();
        walkIn.setReservationCode("WALK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        // Guest walk-ins are not customer accounts and must never be assigned
        // to the issuing staff member or an arbitrary fallback account.
        walkIn.setCustomer(null);
        walkIn.setIssuedByStaff(staffUser); // Track issuing staff for audit trail
        walkIn.setVehicle(null);
        walkIn.setParkingSpace(space);
        walkIn.setArrivalTime(arr);
        walkIn.setActualArrivalTime(arr);
        walkIn.setDepartureTime(dep);
        walkIn.setStatus(Reservation.ReservationStatus.ACTIVE);
        walkIn.setIsWalkIn(true);
        walkIn.setBaseAmount(basePrice);
        walkIn.setTotalAmount(basePrice);
        // Both already required and format-checked by validateForWalkIn above.
        walkIn.setGuestName(guestName != null && !guestName.trim().isEmpty() ? guestName.trim() : null);
        walkIn.setContactPhone(contactPhone != null && !contactPhone.trim().isEmpty() ? contactPhone.trim() : null);
        walkIn.setGuestVehicleRegNumber(cleanPlate);
        walkIn.setGuestContactNumber(walkIn.getContactPhone());
        if (staffUser != null) {
            walkIn.setContactEmail(staffUser.getEmail());
        }

        // Toggle space to OCCUPIED and update facility occupancy
        space.setStatus(ParkingSpace.ParkingSpaceStatus.OCCUPIED);
        parkingSpaceRepository.save(space);
        incrementPhysicalOccupancy(space);

        Reservation saved = reservationRepository.save(walkIn);
        if (paymentRepository != null) {
            Payment payment = new Payment();
            payment.setUser(null);
            payment.setReservationId(saved.getId());
            payment.setAmount(basePrice);
            payment.setPaymentMethod(Payment.PaymentMethod.CASH);
            payment.setPaymentType(Payment.PaymentType.RESERVATION);
            payment.setCardBrand("N/A");
            payment.setMaskedCardNumber("Cash collected at walk-in entry");
            payment.setTransactionCode("WALKIN-" + UUID.randomUUID());
            payment.setStatus(Payment.PaymentStatus.PAID);
            payment.setRemarks("WALK_IN_ENTRY_PAYMENT");
            paymentRepository.save(payment);
        }
        return saved;
    }

    public Reservation updateActiveWalkIn(Long reservationId, String licensePlate, String brand,
                                         String modelName, String color, String guestName,
                                         String contactPhone, Long spaceId, LocalDateTime departureTime) throws Exception {
        return updateActiveWalkIn(reservationId, licensePlate, "CAR", brand, modelName, color, guestName, contactPhone, spaceId, null, departureTime);
    }

    public Reservation updateActiveWalkIn(Long reservationId, String licensePlate, String brand,
                                         String modelName, String color, String guestName,
                                         String contactPhone, Long spaceId,
                                         LocalDateTime arrivalTime, LocalDateTime departureTime) throws Exception {
        return updateActiveWalkIn(reservationId, licensePlate, "CAR", brand, modelName, color, guestName, contactPhone, spaceId, arrivalTime, departureTime);
    }

    public Reservation updateActiveWalkIn(Long reservationId, String licensePlate, String vehicleType, String brand,
                                         String modelName, String color, String guestName,
                                         String contactPhone, Long spaceId,
                                         LocalDateTime arrivalTime, LocalDateTime departureTime) throws Exception {
        Reservation res = getReservationForUpdate(reservationId);
        if (res.getStatus() != Reservation.ReservationStatus.ACTIVE &&
            res.getStatus() != Reservation.ReservationStatus.CONFIRMED) {
            throw new ReservationValidationException("Only active or confirmed reservations can be updated.");
        }
        if (res.getStatus() == Reservation.ReservationStatus.ACTIVE && arrivalTime != null) {
            throw new ReservationValidationException("The actual arrival time of an active walk-in cannot be changed.");
        }
        BigDecimal originalTotal = res.getTotalAmount() != null ? res.getTotalAmount() : BigDecimal.ZERO;

        if (licensePlate == null || !com.example.parkngo.util.SriLankanPlateValidator.isValid(licensePlate)) {
            throw new ReservationValidationException("Enter a valid Sri Lankan license plate, for example WP CA-1234 or CAB-5678.",
                    "licensePlate", "Enter a valid Sri Lankan license plate, for example WP CA-1234 or CAB-5678.");
        }
        if (contactPhone != null && !contactPhone.isBlank()
                && !com.example.parkngo.util.UserInputValidator.isValidSriLankanMobile(contactPhone)) {
            throw new ReservationValidationException("Enter a valid Sri Lankan mobile number, for example 077 123 4567.",
                    "contactPhone", "Enter a valid Sri Lankan mobile number, for example 077 123 4567.");
        }
        if (brand != null && brand.trim().length() > 50) throw new ReservationValidationException("Vehicle brand must be 50 characters or fewer.", "brand", "Vehicle brand must be 50 characters or fewer.");
        if (modelName != null && modelName.trim().length() > 50) throw new ReservationValidationException("Vehicle model must be 50 characters or fewer.", "modelName", "Vehicle model must be 50 characters or fewer.");
        if (color != null && color.trim().length() > 30) throw new ReservationValidationException("Vehicle color must be 30 characters or fewer.", "color", "Vehicle color must be 30 characters or fewer.");
        if (guestName != null && guestName.trim().length() > 60) throw new ReservationValidationException("Guest name must be 60 characters or fewer.", "guestName", "Guest name must be 60 characters or fewer.");

        if (guestName != null && !guestName.trim().isEmpty()) {
            res.setGuestName(guestName.trim());
        }
        if (contactPhone != null && !contactPhone.trim().isEmpty()) {
            res.setContactPhone(com.example.parkngo.util.UserInputValidator.cleanPhone(contactPhone));
        }
        if (licensePlate != null && !licensePlate.trim().isEmpty() && res instanceof WalkInReservation walkIn) {
            walkIn.setGuestVehicleRegNumber(com.example.parkngo.util.SriLankanPlateValidator.normalize(licensePlate));
        }

        if (arrivalTime != null) {
            res.setArrivalTime(arrivalTime);
        }
        if (departureTime != null) {
            res.setDepartureTime(departureTime);
        }
        if (res.getArrivalTime() == null || res.getDepartureTime() == null
                || !res.getDepartureTime().isAfter(res.getArrivalTime())) {
            throw new ReservationValidationException("Departure time must be after arrival time.", "departureTime", "Departure time must be after arrival time.");
        }
        if (res.getArrivalTime() != null && res.getDepartureTime() != null && res.getDepartureTime().isAfter(res.getArrivalTime())) {
            long minutes = java.time.Duration.between(res.getArrivalTime(), res.getDepartureTime()).toMinutes();
            long hours = Math.max(1, (minutes + 59) / 60);
            if (res.getParkingSpace() != null && res.getParkingSpace().getHourlyRate() != null) {
                BigDecimal walkInHourlyRate = res.getParkingSpace().getHourlyRate()
                        .multiply(WALK_IN_PRICE_MULTIPLIER)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal newTotal = walkInHourlyRate.multiply(BigDecimal.valueOf(hours))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                res.setBaseAmount(newTotal);
                res.setTotalAmount(newTotal);
            }
        }
        if (res.getStatus() == Reservation.ReservationStatus.ACTIVE
                && res.getTotalAmount() != null
                && res.getTotalAmount().compareTo(originalTotal) > 0
                && paymentRepository != null) {
            BigDecimal additionalDue = res.getTotalAmount().subtract(originalTotal);
            Payment extensionPayment = new Payment();
            extensionPayment.setReservationId(res.getId());
            extensionPayment.setAmount(additionalDue);
            extensionPayment.setPaymentMethod(Payment.PaymentMethod.CASH);
            extensionPayment.setPaymentType(Payment.PaymentType.RESERVATION);
            extensionPayment.setCardBrand("N/A");
            extensionPayment.setMaskedCardNumber("Cash collected for walk-in extension");
            extensionPayment.setTransactionCode("WALKIN-EXT-" + UUID.randomUUID());
            extensionPayment.setStatus(Payment.PaymentStatus.PAID);
            extensionPayment.setRemarks("WALK_IN_EXTENSION_PAYMENT");
            paymentRepository.save(extensionPayment);
        }

        // Update space if changed
        if (spaceId != null && (res.getParkingSpace() == null || !spaceId.equals(res.getParkingSpace().getId()))) {
            ParkingSpace newSpace = parkingSpaceRepository.findByIdForUpdate(spaceId)
                    .orElseThrow(() -> new ReservationValidationException("Selected parking space not found"));
            requireAllocationType(newSpace, true);
            java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
            reservationValidator.validateParkingSpace(newSpace, true, res.getId(),
                    res.getArrivalTime(), res.getDepartureTime(), errors);
            if (!errors.isEmpty()) {
                throw new ReservationValidationException("The selected parking space is not available.", errors);
            }
            if (newSpace.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE) {
                if (res.getParkingSpace() != null) {
                    decrementPhysicalOccupancyIfOccupied(res);
                    res.getParkingSpace().setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
                    parkingSpaceRepository.save(res.getParkingSpace());
                }
                newSpace.setStatus(ParkingSpace.ParkingSpaceStatus.OCCUPIED);
                parkingSpaceRepository.save(newSpace);
                incrementPhysicalOccupancy(newSpace);
                res.setParkingSpace(newSpace);
            } else {
                throw new ReservationValidationException("Selected parking space is not available.");
            }
        }

        // Online reservations retain their registered vehicle. Walk-ins have
        // no VehicleID by design and keep their guest plate on the subclass.
        Vehicle v = res.getVehicle();
        if (v != null) {
            if (licensePlate != null && !licensePlate.trim().isEmpty()) {
                String cleanPlate = com.example.parkngo.util.SriLankanPlateValidator.normalize(licensePlate);
                v.setRegistrationNumber(cleanPlate);
            }
            if (vehicleType != null && !vehicleType.trim().isEmpty()) {
                v.setVehicleType(vehicleType.trim());
            }
            if (brand != null && !brand.trim().isEmpty()) v.setBrand(brand.trim());
            if (modelName != null && !modelName.trim().isEmpty()) v.setModel(modelName.trim());
            if (color != null && !color.trim().isEmpty()) v.setColor(color.trim());
            vehicleRepository.save(v);
        }

        return reservationRepository.save(res);
    }

    public boolean isSpaceAvailable(Long spaceId, LocalDateTime arrivalTime, LocalDateTime departureTime) {
        LocalDateTime bufferArrival = arrivalTime.minusMinutes(ReservationValidator.TURNOVER_BUFFER_MINUTES);
        LocalDateTime bufferDeparture = departureTime.plusMinutes(ReservationValidator.TURNOVER_BUFFER_MINUTES);
        List<Reservation> conflicting = reservationRepository.findConflictingReservations(spaceId, bufferArrival, bufferDeparture);
        return conflicting.isEmpty();
    }

    private void requireAllocationType(ParkingSpace space, boolean walkIn) {
        if (space == null) {
            throw new ReservationValidationException("A parking space is required.");
        }
        String allocation = space.getAllocationType() == null
                ? "BOTH" : space.getAllocationType().trim().toUpperCase();
        boolean allowed = walkIn
                ? !"ONLINE_BOOKABLE".equals(allocation)
                : !"WALKIN_ONLY".equals(allocation);
        if (!allowed) {
            throw new ReservationValidationException(
                    walkIn ? "This bay is reserved for online bookings." : "This bay is reserved for walk-in vehicles.");
        }
    }

    private boolean isAllocationAllowed(ParkingSpace space, boolean walkIn) {
        if (space == null) return false;
        String allocation = space.getAllocationType() == null
                    ? "GENERAL" : space.getAllocationType().trim().toUpperCase();
        return walkIn
                    ? !"ONLINE_BOOKABLE".equals(allocation)
                    : !"WALKIN_ONLY".equals(allocation);
    }

    private Reservation getReservationForUpdate(Long id) throws Exception {
        if (id == null) {
            throw new ReservationValidationException("Reservation ID is required");
        }
        Optional<Reservation> locked = reservationRepository.findByIdForUpdate(id);
        if (locked != null && locked.isPresent()) {
            return locked.get();
        }
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationValidationException("Reservation not found with ID: " + id));
    }

    public Reservation getReservationById(Long id) throws Exception {
        if (id == null) {
            throw new ReservationValidationException("Reservation ID is required");
        }
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationValidationException("Reservation not found with ID: " + id));
    }

    public Reservation getReservationByCode(String code) throws Exception {
        return reservationRepository.findByReservationCode(code)
                .orElseThrow(() -> new ReservationValidationException("Reservation not found with code: " + code));
    }

    public List<Reservation> getCustomerReservations(Long customerId) {
        return reservationRepository.findByCustomerId(customerId);
    }

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    public List<Reservation> getActiveReservations() {
        return reservationRepository.findActiveReservations();
    }

    public List<Reservation> searchReservations(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return reservationRepository.findActiveReservations();
        }
        return reservationRepository.searchReservations(searchTerm.trim());
    }

    public void confirmReservation(Long reservationId) throws Exception {
        Reservation reservation = getReservationForUpdate(reservationId);
        if (reservation.getStatus() == Reservation.ReservationStatus.OVERSTAY_PAYMENT_DUE) {
            completeOverstayAfterPayment(reservation);
            return;
        }
        reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);
        reservation.setPaymentHoldExpiresAt(null);
        reservationRepository.save(reservation);
    }

    public void verifyAndCheckIn(Long reservationId) throws Exception {
        Reservation res = getReservationForUpdate(reservationId);
        reservationValidator.validateForCheckIn(res);

        res.setStatus(Reservation.ReservationStatus.ACTIVE);
        res.setActualArrivalTime(LocalDateTime.now());

        // Toggle parking bay to OCCUPIED
        ParkingSpace space = res.getParkingSpace();
        if (space != null) {
            space.setStatus(ParkingSpace.ParkingSpaceStatus.OCCUPIED);
            parkingSpaceRepository.save(space);
            incrementPhysicalOccupancy(space);
        }

        // Settle any pending cash-at-entry payment
        List<Payment> payments = (paymentRepository != null) ? paymentRepository.findByReservationId(reservationId) : List.of();
        boolean cashSettled = false;
        BigDecimal settledAmount = BigDecimal.ZERO;
        for (Payment p : payments) {
            if (p.getStatus() == Payment.PaymentStatus.PENDING) {
                p.setStatus(Payment.PaymentStatus.PAID);
                p.setMaskedCardNumber("Cash Settled at Gate Entry");
                if (paymentRepository != null) {
                    paymentRepository.save(p);
                }
                cashSettled = true;
                if (p.getAmount() != null) {
                    settledAmount = settledAmount.add(p.getAmount());
                }
            }
        }

        // Leave a notification for the customer after on-site payment is settled
        if (cashSettled) {
            try {
                if (notificationService != null && res.getCustomer() != null) {
                    String bookingRef = (res.getReservationCode() != null && !res.getReservationCode().isEmpty())
                            ? res.getReservationCode() : String.valueOf(res.getId());
                    notificationService.sendReservationNotification(
                            res.getCustomer().getId(),
                            "Payment Received (On-Site)",
                            "On-site payment of LKR " + settledAmount + " for reservation #" + bookingRef + " has been received and settled at gate entry.",
                            Notification.NotificationType.PAYMENT_SUCCESSFUL,
                            res.getId()
                    );
                }
            } catch (Exception ignored) {}
        }

        reservationRepository.save(res);
    }

    private void completeOverstayAfterPayment(Reservation reservation) throws Exception {
        if (paymentRepository == null) {
            throw new ReservationValidationException("Overstay payment could not be verified.");
        }
        BigDecimal due = reservation.getOverstayCharge() != null ? reservation.getOverstayCharge() : BigDecimal.ZERO;
        LocalDateTime departure = reservation.getActualDepartureTime();
        boolean settled = paymentRepository.findByReservationIdAndRemarks(reservation.getId(), "OVERSTAY_PAYMENT").stream()
                .filter(p -> p.getStatus() != null && p.getStatus().isSettled())
                .filter(p -> p.getAmount() != null && p.getAmount().compareTo(due) == 0)
                .anyMatch(p -> departure == null || p.getCreatedAt() == null || !p.getCreatedAt().isBefore(departure));
        if (!settled) {
            throw new ReservationValidationException("Outstanding overstay payment must be settled before checkout.");
        }

        paymentRepository.findByReservationId(reservation.getId()).stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                .filter(p -> "OVERSTAY_PAYMENT_DUE".equals(p.getRemarks()))
                .forEach(p -> {
                    p.setStatus(Payment.PaymentStatus.FAILED);
                    p.setFailureReason("Settled by a subsequent overstay payment transaction");
                    paymentRepository.save(p);
                });
        reservation.setStatus(Reservation.ReservationStatus.COMPLETED);
        releaseCompletedSpace(reservation);
        Reservation saved = reservationRepository.save(reservation);
    }

    public Reservation processCheckOutAndOverstay(Long reservationId) throws Exception {
        Reservation res = getReservationForUpdate(reservationId);
        reservationValidator.validateForCheckOut(res);

        LocalDateTime actualExit = LocalDateTime.now();
        res.setActualDepartureTime(actualExit);

        // An overstay is a billable intermediate state. Keep the bay occupied
        // until the outstanding charge has been settled.
        if (actualExit.isAfter(res.getDepartureTime())) {
            long overstayMinutes = Duration.between(res.getDepartureTime(), actualExit).toMinutes();
            long overstayHours = Math.max(1, (overstayMinutes + 59) / 60);
            BigDecimal hourlyRate = (res.getParkingSpace() != null && res.getParkingSpace().getHourlyRate() != null)
                    ? res.getParkingSpace().getHourlyRate()
                    : BigDecimal.valueOf(250.00);
            BigDecimal penaltyFee = hourlyRate.multiply(new BigDecimal("1.50"))
                    .multiply(BigDecimal.valueOf(overstayHours));

            res.setOverstayCharge(penaltyFee);
            res.setTotalAmount(res.getTotalAmount().add(penaltyFee));
            res.setStatus(Reservation.ReservationStatus.OVERSTAYING);

            if (paymentRepository == null) {
                throw new ReservationValidationException("Overstay payment cannot be recorded.");
            }
            Payment outstanding = new Payment();
            outstanding.setUser(res.getCustomer());
            outstanding.setReservationId(res.getId());
            outstanding.setAmount(penaltyFee);
            outstanding.setPaymentMethod(Payment.PaymentMethod.CASH_ON_ENTRY);
            outstanding.setPaymentType(Payment.PaymentType.RESERVATION);
            outstanding.setCardBrand("N/A");
            outstanding.setMaskedCardNumber("Outstanding overstay charge");
            outstanding.setTransactionCode("OVERSTAY-" + UUID.randomUUID());
            outstanding.setStatus(Payment.PaymentStatus.PENDING);
            outstanding.setRemarks("OVERSTAY_PAYMENT_DUE");
            paymentRepository.save(outstanding);

            res.setStatus(Reservation.ReservationStatus.OVERSTAY_PAYMENT_DUE);
            notifyNextReservationRisk(res);
            return reservationRepository.save(res);
        }

        res.setStatus(Reservation.ReservationStatus.COMPLETED);

        // Release parking space back to AVAILABLE and update facility occupancy
        ParkingSpace space = res.getParkingSpace();
        if (space != null) {
            space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
            parkingSpaceRepository.save(space);
            if (space.getFacility() != null && parkingFacilityRepository != null) {
                ParkingFacility fac = space.getFacility();
                int cur = fac.getCurrentOccupancy() != null ? fac.getCurrentOccupancy() : 0;
                fac.setCurrentOccupancy(Math.max(0, cur - 1));
                parkingFacilityRepository.save(fac);
            }
        }

        Reservation saved = reservationRepository.save(res);

        return saved;
    }

    private void releaseCompletedSpace(Reservation reservation) {
        ParkingSpace space = reservation.getParkingSpace();
        if (space != null) {
            space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
            parkingSpaceRepository.save(space);
            if (space.getFacility() != null && parkingFacilityRepository != null) {
                ParkingFacility fac = space.getFacility();
                int cur = fac.getCurrentOccupancy() != null ? fac.getCurrentOccupancy() : 0;
                fac.setCurrentOccupancy(Math.max(0, cur - 1));
                parkingFacilityRepository.save(fac);
            }
        }
    }

    private void incrementPhysicalOccupancy(ParkingSpace space) {
        if (space == null || space.getFacility() == null || parkingFacilityRepository == null) return;
        ParkingFacility facility = space.getFacility();
        int current = facility.getCurrentOccupancy() != null ? facility.getCurrentOccupancy() : 0;
        int capacity = facility.getTotalCapacity() != null ? facility.getTotalCapacity() : Integer.MAX_VALUE;
        facility.setCurrentOccupancy(Math.min(capacity, current + 1));
        parkingFacilityRepository.save(facility);
    }

    private void decrementPhysicalOccupancyIfOccupied(Reservation reservation) {
        if (reservation == null || reservation.getActualArrivalTime() == null) return;
        ParkingSpace space = reservation.getParkingSpace();
        if (space == null || space.getFacility() == null || parkingFacilityRepository == null) return;
        ParkingFacility facility = space.getFacility();
        int current = facility.getCurrentOccupancy() != null ? facility.getCurrentOccupancy() : 0;
        facility.setCurrentOccupancy(Math.max(0, current - 1));
        parkingFacilityRepository.save(facility);
    }

    private void notifyNextReservationRisk(Reservation reservation) {
        if (notificationService == null || reservation == null || reservation.getParkingSpace() == null) return;
        List<Reservation> next = reservationRepository.findNextReservations(
                reservation.getParkingSpace().getId(), reservation.getDepartureTime());
        if (next.isEmpty()) return;
        Reservation following = next.get(0);
        LocalDateTime availableBy = LocalDateTime.now().plusMinutes(NEXT_RESERVATION_PREPARATION_MINUTES);
        if (!following.getArrivalTime().isAfter(availableBy)) {
            try {
                if (following.getCustomer() != null) {
                    notificationService.sendReservationNotification(
                            following.getCustomer().getId(),
                            "Parking bay delayed",
                            "Your reserved bay may be delayed because the previous vehicle has overstayed. Please contact staff for assistance.",
                            Notification.NotificationType.OVERSTAY_ALERT,
                            following.getId());
                }
                if (reservation.getCustomer() != null) {
                    notificationService.sendReservationNotification(
                            reservation.getCustomer().getId(),
                            "Overstay payment required",
                            "Your vehicle is blocking the next reservation. Please settle the overstay charge before leaving.",
                            Notification.NotificationType.OVERSTAY_ALERT,
                            reservation.getId());
                }
            } catch (Exception e) {
                log.warn("Unable to notify customers about overstay risk for reservation {}: {}",
                        reservation.getId(), e.getMessage());
            }
        }
    }

    public void cancelReservation(Long reservationId) throws Exception {
        cancelReservation(reservationId, null);
    }

    public void cancelReservation(Long reservationId, User currentUser) throws Exception {
        Reservation reservation = getReservationForUpdate(reservationId);
        reservationValidator.validateForCancellation(reservation, currentUser);
        boolean physicallyOccupied = reservation.getActualArrivalTime() != null
                && (reservation.getStatus() == Reservation.ReservationStatus.ACTIVE
                || reservation.getStatus() == Reservation.ReservationStatus.OVERSTAYING
                || reservation.getStatus() == Reservation.ReservationStatus.OVERSTAY_PAYMENT_DUE);

        reservation.setStatus(Reservation.ReservationStatus.CANCELLED);

        // Ensure parking space is available and update facility occupancy
        ParkingSpace space = reservation.getParkingSpace();
        if (space != null && (space.getStatus() == ParkingSpace.ParkingSpaceStatus.RESERVED || space.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED)) {
            space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
            parkingSpaceRepository.save(space);
            if (physicallyOccupied && space.getFacility() != null && parkingFacilityRepository != null) {
                ParkingFacility fac = space.getFacility();
                int cur = fac.getCurrentOccupancy() != null ? fac.getCurrentOccupancy() : 0;
                fac.setCurrentOccupancy(Math.max(0, cur - 1));
                parkingFacilityRepository.save(fac);
            }
        }

        reservationRepository.save(reservation);

        BigDecimal paidAmount = (paymentService != null && paymentService.getPaidAmountForReservation(reservationId) != null)
                ? paymentService.getPaidAmountForReservation(reservationId) : BigDecimal.ZERO;
        boolean hadPaidOnline = paidAmount.compareTo(BigDecimal.ZERO) > 0;

        // Refund any paid payments for this reservation
        if (paymentService != null) {
            try {
                paymentService.refundReservationPayments(reservationId, "Customer cancelled reservation");
            } catch (Exception e) {
                log.warn("Failed to refund payments for reservation {}: {}", reservationId, e.getMessage());
            }
        }

        // Give back whatever this booking spent at checkout (a membership pass visit, or discount coupon slot)
        if (checkoutService != null) {
            try {
                checkoutService.rollbackCheckout(reservationId);
            } catch (Exception ignored) {
            }
        }

        // Safe notification call
        try {
            if (notificationService != null && reservation.getCustomer() != null) {
                String title = hadPaidOnline ? "Booking Cancelled & Refunded" : "Booking Cancelled";
                String body = hadPaidOnline
                        ? "Your reservation for vehicle " + (reservation.getVehicle() != null ? reservation.getVehicle().getRegistrationNumber() : "") + " has been cancelled. A 100% full refund of LKR " + paidAmount + " has been credited to your card."
                        : "Your reservation for vehicle " + (reservation.getVehicle() != null ? reservation.getVehicle().getRegistrationNumber() : "") + " has been cancelled. No charge was incurred.";
                notificationService.sendReservationNotification(
                        reservation.getCustomer().getId(),
                        title,
                        body,
                        Notification.NotificationType.RESERVATION_CANCELLED,
                        reservation.getId()
                );
            }
        } catch (Exception ignored) {}
    }

    public Reservation updateReservationSchedule(Long reservationId,
                                                 LocalDateTime arrivalTime,
                                                 LocalDateTime departureTime,
                                                 User currentUser) throws Exception {
        return updateReservationSchedule(reservationId, arrivalTime, departureTime, null, null, currentUser);
    }

    public Reservation updateReservationSchedule(Long reservationId,
                                                 LocalDateTime arrivalTime,
                                                 LocalDateTime departureTime,
                                                 Long newSpaceId,
                                                 User currentUser) throws Exception {
        return updateReservationSchedule(reservationId, arrivalTime, departureTime, newSpaceId, null, currentUser);
    }

    public Reservation updateReservationSchedule(Long reservationId,
                                                 LocalDateTime arrivalTime,
                                                 LocalDateTime departureTime,
                                                 Long newSpaceId,
                                                 Long newVehicleId,
                                                 User currentUser) throws Exception {
        return updateReservationSchedule(reservationId, arrivalTime, departureTime, newSpaceId, newVehicleId, null, null, null, null, null, currentUser);
    }

    public Reservation updateReservationSchedule(Long reservationId,
                                                 LocalDateTime arrivalTime,
                                                 LocalDateTime departureTime,
                                                 Long newSpaceId,
                                                 Long newVehicleId,
                                                 String paymentMethod,
                                                 String cardNumber,
                                                 String cardholderName,
                                                 String expiryDate,
                                                 String cvc,
                                                 User currentUser) throws Exception {
        return updateReservationSchedule(reservationId, arrivalTime, departureTime, newSpaceId, newVehicleId, null,
                paymentMethod, cardNumber, cardholderName, expiryDate, cvc, currentUser);
    }

    public Reservation updateReservationSchedule(Long reservationId,
                                                 LocalDateTime arrivalTime,
                                                 LocalDateTime departureTime,
                                                 Long newSpaceId,
                                                 Long newVehicleId,
                                                 List<Long> serviceIds,
                                                 String paymentMethod,
                                                 String cardNumber,
                                                 String cardholderName,
                                                 String expiryDate,
                                                 String cvc,
                                                 User currentUser) throws Exception {
        Reservation existing = getReservationById(reservationId);
        reservationValidator.validateForEdit(existing, currentUser);

        // Vehicle change
        Vehicle targetVehicle = existing.getVehicle();
        if (newVehicleId != null && (targetVehicle == null || !newVehicleId.equals(targetVehicle.getId()))) {
            Vehicle candidateVehicle = vehicleRepository.findById(newVehicleId).orElse(null);
            if (candidateVehicle != null) {
                targetVehicle = candidateVehicle;
            }
        }

        // Space change
        ParkingSpace currentSpace = existing.getParkingSpace();
        ParkingSpace targetSpace = currentSpace;
        boolean spaceChanged = false;
        if (newSpaceId != null && (currentSpace == null || !newSpaceId.equals(currentSpace.getId()))) {
            ParkingSpace candidateSpace = parkingSpaceRepository.findByIdForUpdate(newSpaceId).orElse(null);
            if (candidateSpace == null) {
                throw new ReservationValidationException(
                        "Selected parking bay could not be found.",
                        "parkingSpaceId",
                        "The selected parking bay no longer exists. Please choose another."
                );
            }
            targetSpace = candidateSpace;
            spaceChanged = true;
        } else if (currentSpace != null) {
            parkingSpaceRepository.findByIdForUpdate(currentSpace.getId());
        }

        Reservation candidate = new Reservation();
        candidate.setId(existing.getId());
        candidate.setCustomer(existing.getCustomer());
        candidate.setVehicle(targetVehicle);
        candidate.setParkingSpace(targetSpace);
        candidate.setArrivalTime(arrivalTime);
        candidate.setDepartureTime(departureTime);
        reservationValidator.validateForBooking(candidate, serviceIds, null);

        long minutes = Duration.between(arrivalTime, departureTime).toMinutes();
        long hours = Math.max(1, (minutes + 59) / 60);
        BigDecimal baseAmount = targetSpace.getHourlyRate().multiply(BigDecimal.valueOf(hours));

        // Update add-on services if provided
        BigDecimal serviceAmount = BigDecimal.ZERO;
        List<ReservationServiceItem> newServiceItems = new ArrayList<>();
        if (serviceIds != null) {
            for (Long sId : serviceIds) {
                if (sId == null) continue;
                FacilityService service = facilityServiceRepository.findById(sId).orElse(null);
                if (service != null && service.isAvailable()) {
                    ReservationServiceItem item = new ReservationServiceItem();
                    item.setReservation(existing);
                    item.setService(service);
                    item.setServicePrice(service.getPrice());
                    newServiceItems.add(item);
                    serviceAmount = serviceAmount.add(service.getPrice());
                }
            }
        } else {
            serviceAmount = existing.getServiceAmount() != null ? existing.getServiceAmount() : BigDecimal.ZERO;
            if (existing.getServiceItems() != null) {
                newServiceItems.addAll(existing.getServiceItems());
            }
        }

        // Recalculate discount
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal preDiscountAmount = baseAmount.add(serviceAmount);
        if (existing.getAppliedDiscount() != null) {
            discountAmount = discountService.calculateDiscount(existing.getAppliedDiscount(), baseAmount);
        } else if (existing.getAppliedSubscriptionId() != null && existing.getCustomer() != null) {
            Subscription activePass = subscriptionService.getActiveMembership(existing.getCustomer().getId());
            if (activePass != null && activePass.getMembershipPackage() != null && activePass.getMembershipPackage().getDiscountPercentage() != null) {
                discountAmount = preDiscountAmount.multiply(
                        activePass.getMembershipPackage().getDiscountPercentage().divide(BigDecimal.valueOf(100))
                );
            }
        }
        BigDecimal newTotal = preDiscountAmount.subtract(discountAmount).max(BigDecimal.ZERO);

        // Reconcile payments
        BigDecimal previouslyPaid = paymentService.getPaidAmountForReservation(reservationId);
        List<Payment> existingPayments = paymentRepository.findByReservationId(reservationId);
        Payment pendingPayment = existingPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                .findFirst()
                .orElse(null);

        if (previouslyPaid.compareTo(BigDecimal.ZERO) > 0) {
            // Originally paid with Card (online payment)
            if (newTotal.compareTo(previouslyPaid) < 0) {
                // Price decrease: process partial refund back to card
                BigDecimal refundDelta = previouslyPaid.subtract(newTotal);
                paymentService.processPartialRefund(currentUser, reservationId, refundDelta, "Booking modification refund");
            } else if (newTotal.compareTo(previouslyPaid) > 0) {
                // Price increase: additional payment required
                BigDecimal additionalDue = newTotal.subtract(previouslyPaid);
                String payMethod = (paymentMethod != null && !paymentMethod.trim().isEmpty())
                        ? paymentMethod.trim().toUpperCase() : "CREDIT_CARD";
                if ("CARD".equals(payMethod)) payMethod = "CREDIT_CARD";
                if ("CASH".equals(payMethod)) payMethod = "CASH_ON_ENTRY";

                if ("CREDIT_CARD".equals(payMethod) || "DEBIT_CARD".equals(payMethod)) {
                    String cleanNum = cardNumber == null ? "" : cardNumber.replaceAll("[\\s-]", "");
                    if (!cleanNum.matches("\\d{13,16}")) {
                        throw new ReservationValidationException(
                                "Card number must be 13-16 digits for additional payment of LKR " + additionalDue,
                                "cardNumber",
                                "Please enter a valid card number (13-16 digits) to pay the difference of LKR " + additionalDue
                        );
                    }
                    if (!PaymentService.isValidLuhn(cleanNum)) {
                        throw new ReservationValidationException(
                                "Invalid card number. Checksum verification failed.",
                                "cardNumber",
                                "Invalid card number. Please check for mistyped digits (checksum failed)."
                        );
                    }
                    if (expiryDate == null || !PaymentService.isValidExpiryDate(expiryDate)) {
                        throw new ReservationValidationException(
                                "Invalid or expired card date (MM/YY)",
                                "expiryDate",
                                "Please enter a valid, unexpired card date in MM/YY format (e.g. 12/28)."
                        );
                    }
                    if (cvc == null || !PaymentService.isValidCvc(cvc)) {
                        throw new ReservationValidationException(
                                "Invalid CVC/CVV security code",
                                "cvc",
                                "Please enter a valid 3 or 4 digit security code (CVC/CVV)."
                        );
                    }
                    Payment extraPayment = paymentService.processReservationPayment(
                            currentUser != null ? currentUser : existing.getCustomer(),
                            reservationId,
                            additionalDue,
                            payMethod,
                            cardNumber
                    );
                    if (extraPayment.getStatus() == Payment.PaymentStatus.FAILED) {
                        String reason = extraPayment.getFailureReason() != null ? extraPayment.getFailureReason() : "Additional payment failed.";
                        throw new ReservationValidationException(
                                "Payment failed: " + reason,
                                "cardNumber",
                                reason
                        );
                    }
                    if (extraPayment.getStatus() == Payment.PaymentStatus.PAID) {
                        try {
                            if (notificationService != null && existing.getCustomer() != null) {
                                String bookingRef = (existing.getReservationCode() != null && !existing.getReservationCode().isEmpty())
                                        ? existing.getReservationCode() : String.valueOf(existing.getId());
                                String txnText = (extraPayment.getTransactionCode() != null && !extraPayment.getTransactionCode().isEmpty())
                                        ? " (Txn: " + extraPayment.getTransactionCode() + ")" : "";
                                notificationService.sendReservationNotification(
                                        existing.getCustomer().getId(),
                                        "Payment Received",
                                        "Additional online payment of LKR " + additionalDue + " for reservation #" + bookingRef + " has been received successfully" + txnText + ".",
                                        Notification.NotificationType.PAYMENT_SUCCESSFUL,
                                        existing.getId()
                                );
                            }
                        } catch (Exception ignored) {}
                    }
                } else if ("CASH_ON_ENTRY".equals(payMethod)) {
                    // Settle additional amount at gate
                    Payment extraPending = new Payment();
                    extraPending.setUser(currentUser != null ? currentUser : existing.getCustomer());
                    extraPending.setReservationId(reservationId);
                    extraPending.setAmount(additionalDue);
                    extraPending.setPaymentMethod(Payment.PaymentMethod.CASH_ON_ENTRY);
                    extraPending.setPaymentType(Payment.PaymentType.RESERVATION);
                    extraPending.setCardBrand("N/A");
                    extraPending.setMaskedCardNumber("Cash at Entry Gate (Adjustment)");
                    extraPending.setTransactionCode("GATE-" + System.currentTimeMillis() % 10000000);
                    extraPending.setStatus(Payment.PaymentStatus.PENDING);
                    paymentRepository.save(extraPending);
                }
            }
        } else if (pendingPayment != null) {
            // Originally Pay at Gate (no online card payment made)
            String payMethod = paymentMethod != null ? paymentMethod.trim().toUpperCase() : "";
            if (("CREDIT_CARD".equals(payMethod) || "DEBIT_CARD".equals(payMethod) || "CARD".equals(payMethod))
                    && cardNumber != null && !cardNumber.trim().isEmpty()) {
                // Customer decides to convert from Pay at Gate to Card payment now
                String cleanNum = cardNumber.replaceAll("[\\s-]", "");
                if (!cleanNum.matches("\\d{13,16}")) {
                    throw new ReservationValidationException(
                            "Card number must be 13-16 digits",
                            "cardNumber",
                            "Please enter a valid card number (13-16 digits)."
                    );
                }
                if (!PaymentService.isValidLuhn(cleanNum)) {
                    throw new ReservationValidationException(
                            "Invalid card number. Checksum verification failed.",
                            "cardNumber",
                            "Invalid card number. Please check for mistyped digits (checksum failed)."
                    );
                }
                if (expiryDate == null || !PaymentService.isValidExpiryDate(expiryDate)) {
                    throw new ReservationValidationException(
                            "Invalid or expired card date (MM/YY)",
                            "expiryDate",
                            "Please enter a valid, unexpired card date in MM/YY format (e.g. 12/28)."
                    );
                }
                if (cvc == null || !PaymentService.isValidCvc(cvc)) {
                    throw new ReservationValidationException(
                            "Invalid CVC/CVV security code",
                            "cvc",
                            "Please enter a valid 3 or 4 digit security code (CVC/CVV)."
                    );
                }
                Payment convertedPayment = paymentService.processReservationPayment(
                        currentUser != null ? currentUser : existing.getCustomer(),
                        reservationId,
                        newTotal,
                        "CREDIT_CARD",
                        cardNumber
                );
                if (convertedPayment.getStatus() == Payment.PaymentStatus.FAILED) {
                    String reason = convertedPayment.getFailureReason() != null ? convertedPayment.getFailureReason() : "Card payment failed.";
                    throw new ReservationValidationException(
                            "Payment failed: " + reason,
                            "cardNumber",
                            reason
                    );
                }
                if (convertedPayment.getStatus() == Payment.PaymentStatus.PAID) {
                    try {
                        if (notificationService != null && existing.getCustomer() != null) {
                            String bookingRef = (existing.getReservationCode() != null && !existing.getReservationCode().isEmpty())
                                    ? existing.getReservationCode() : String.valueOf(existing.getId());
                            String txnText = (convertedPayment.getTransactionCode() != null && !convertedPayment.getTransactionCode().isEmpty())
                                    ? " (Txn: " + convertedPayment.getTransactionCode() + ")" : "";
                            notificationService.sendReservationNotification(
                                    existing.getCustomer().getId(),
                                    "Payment Received",
                                    "Online payment of LKR " + newTotal + " for reservation #" + bookingRef + " has been received successfully" + txnText + ".",
                                    Notification.NotificationType.PAYMENT_SUCCESSFUL,
                                    existing.getId()
                            );
                        }
                    } catch (Exception ignored) {}
                }
                // Void previous pending payment
                pendingPayment.setStatus(Payment.PaymentStatus.FAILED);
                pendingPayment.setFailureReason("Converted to online card payment");
                paymentRepository.save(pendingPayment);
            } else {
                // Keep as Pay at Gate with updated amount
                pendingPayment.setAmount(newTotal);
                paymentRepository.save(pendingPayment);
            }
        }

        if (serviceIds != null) {
            existing.getServiceItems().clear();
            for (ReservationServiceItem item : newServiceItems) {
                existing.getServiceItems().add(item);
            }
        }
        existing.setVehicle(targetVehicle);
        existing.setArrivalTime(arrivalTime);
        existing.setDepartureTime(departureTime);
        existing.setBaseAmount(baseAmount);
        existing.setServiceAmount(serviceAmount);
        existing.setDiscountAmount(discountAmount);
        existing.setTotalAmount(newTotal);

        if (spaceChanged) {
            existing.setParkingSpace(targetSpace);
        }

        Reservation saved = reservationRepository.save(existing);

        // Safe notification call
        try {
            if (notificationService != null && saved.getCustomer() != null) {
                notificationService.sendReservationNotification(
                        saved.getCustomer().getId(),
                        "Booking Updated",
                        "Your reservation for vehicle " + (saved.getVehicle() != null ? saved.getVehicle().getRegistrationNumber() : "") + " has been updated successfully.",
                        Notification.NotificationType.RESERVATION_MODIFIED,
                        saved.getId()
                );
            }
        } catch (Exception ignored) {}

        return saved;
    }

    /**
     * Automated background scheduler: Runs every 60 seconds to auto-expire
     * un-checked-in CONFIRMED reservations whose scheduled departure time has passed.
     */
    @Scheduled(fixedRate = 60000)
    public void autoExpireStaleReservations() {
        try {
            LocalDateTime cutoff = LocalDateTime.now();
            List<Reservation> expiredHolds = reservationRepository.findExpiredPaymentHolds(cutoff);
            for (Reservation hold : expiredHolds) {
                hold.setStatus(Reservation.ReservationStatus.EXPIRED);
                hold.setPaymentHoldExpiresAt(null);
                reservationRepository.save(hold);
                ParkingSpace heldSpace = hold.getParkingSpace();
                if (heldSpace != null && heldSpace.getStatus() == ParkingSpace.ParkingSpaceStatus.RESERVED) {
                    heldSpace.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
                    parkingSpaceRepository.save(heldSpace);
                }
                if (paymentRepository != null) {
                    paymentRepository.findByReservationId(hold.getId()).stream()
                            .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                            .forEach(p -> {
                                p.setStatus(Payment.PaymentStatus.FAILED);
                                p.setFailureReason("Reservation payment hold expired");
                                paymentRepository.save(p);
                            });
                }
            }
            List<Reservation> staleList = reservationRepository.findStaleConfirmedReservations(cutoff);
            if (!staleList.isEmpty()) {
                for (Reservation r : staleList) {
                    r.setStatus(Reservation.ReservationStatus.EXPIRED);
                    reservationRepository.save(r);
                    ParkingSpace space = r.getParkingSpace();
                    if (space != null && space.getStatus() == ParkingSpace.ParkingSpaceStatus.RESERVED) {
                        space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
                        parkingSpaceRepository.save(space);
                    }
                }
                log.info("Auto-expired {} stale confirmed reservation(s) past scheduled departure time.", staleList.size());
            }
        } catch (Exception e) {
            log.error("Error during auto-expiry of stale reservations: {}", e.getMessage());
        }
    }

    private String generateReservationCode() {
        return "RES-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private void copyReservationFields(Reservation source, Reservation target) {
        target.setId(source.getId());
        target.setReservationCode(source.getReservationCode());
        target.setCustomer(source.getCustomer());
        target.setIssuedByStaff(source.getIssuedByStaff());
        target.setContactEmail(source.getContactEmail());
        target.setContactPhone(source.getContactPhone());
        target.setGuestName(source.getGuestName());
        target.setVehicle(source.getVehicle());
        target.setParkingSpace(source.getParkingSpace());
        target.setArrivalTime(source.getArrivalTime());
        target.setDepartureTime(source.getDepartureTime());
        target.setActualArrivalTime(source.getActualArrivalTime());
        target.setActualDepartureTime(source.getActualDepartureTime());
        target.setStatus(source.getStatus());
        target.setBaseAmount(source.getBaseAmount());
        target.setServiceAmount(source.getServiceAmount());
        target.setDiscountAmount(source.getDiscountAmount());
        target.setTotalAmount(source.getTotalAmount());
        target.setOverstayCharge(source.getOverstayCharge());
        target.setIsWalkIn(source.getIsWalkIn());
        target.setReservationType(source.getReservationType() != null ? source.getReservationType() : "ONLINE");
        target.setAppliedSubscriptionId(source.getAppliedSubscriptionId());
        target.setAppliedDiscount(source.getAppliedDiscount());
        target.setPaymentHoldExpiresAt(source.getPaymentHoldExpiresAt());
        target.setServiceItems(source.getServiceItems());
    }
}
