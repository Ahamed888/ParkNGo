package com.example.parkngo.reservation;

import com.example.parkngo.discount.DiscountService;
import com.example.parkngo.facility.FacilityService;
import com.example.parkngo.facility.FacilityServiceRepository;
import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.user.User;
import com.example.parkngo.vehicle.Vehicle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Dedicated domain validator for Module 3: Reservation Management.
 * Enforces field-level syntax, temporal bounds, business rules,
 * resource allocation policies, and state machine transitions.
 */
@Component
public class ReservationValidator {

    private static final Pattern PLATE_PATTERN = Pattern.compile(
            "^(?:(?:(WP|CP|SP|NW|NC|EP|SG|VA|UW)[\\s-]*)?(?:([A-Z]{1,4})|(\\d{1,3}))[\\s-]*(?:SRI[\\s-]*)?(\\d{1,4})|(?:(WP|CP|SP|NW|NC|EP|SG|VA|UW)[\\s-]*(\\d{1,4}))|[A-Z0-9\\s-]{3,15})$",
            Pattern.CASE_INSENSITIVE
    );

    // Same shape ParkingFacilityService already validates facility contact
    // numbers against - kept consistent across the codebase rather than
    // inventing a second phone format.
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^(?:(?:\\+94|0)[0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{4}|(?:\\+94|0)[0-9]{9})$"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public static final int TURNOVER_BUFFER_MINUTES = 15;
    public static final int EARLY_CHECKIN_MINUTES = 15;

    private final ReservationRepository reservationRepository;
    private final DiscountService discountService;
    private final FacilityServiceRepository facilityServiceRepository;

    public ReservationValidator(ReservationRepository reservationRepository,
                                DiscountService discountService,
                                FacilityServiceRepository facilityServiceRepository) {
        this.reservationRepository = reservationRepository;
        this.discountService = discountService;
        this.facilityServiceRepository = facilityServiceRepository;
    }

    /**
     * Complete validation for online customer reservation booking.
     */
    public void validateForBooking(Reservation reservation, List<Long> serviceIds, String discountCode) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (reservation == null) {
            throw new ReservationValidationException("Reservation details are required.");
        }

        // 1. Validate Facility & Operating Hours (from ParkingSpace if available)
        ParkingFacility facility = (reservation.getParkingSpace() != null)
                ? reservation.getParkingSpace().getFacility()
                : null;
        if (facility != null && facility.getStatus() == ParkingFacility.FacilityStatus.INACTIVE) {
            errors.put("parkingSpaceId", "Parking facility '" + facility.getName() + "' is currently inactive and not available for customer bookings.");
        }

        // 2. Validate Arrival & Departure Times
        validateTimes(reservation.getArrivalTime(), reservation.getDepartureTime(), facility, errors);
        if (reservation.getArrivalTime() != null && reservation.getDepartureTime() != null) {
            Duration stay = Duration.between(reservation.getArrivalTime(), reservation.getDepartureTime());
            long hours = stay.toHours();
            if (hours < 1 || hours > 8 || !stay.equals(Duration.ofHours(hours))) {
                errors.put("departureTime", "Choose a stay of 1 to 8 whole hours.");
            }
        }

        // 3. Validate Vehicle Selection & Double Booking Conflict
        validateVehicle(
                reservation.getVehicle(),
                reservation.getCustomer(),
                reservation.getId(),
                reservation.getArrivalTime(),
                reservation.getDepartureTime(),
                errors
        );

        // 4. Validate Parking Bay Allocation & Overlap
        validateParkingSpace(
                reservation.getParkingSpace(),
                false,
                reservation.getId(),
                reservation.getArrivalTime(),
                reservation.getDepartureTime(),
                errors
        );

        // 5. Validate Contact Info if provided
        if (reservation.getContactEmail() != null && !reservation.getContactEmail().trim().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(reservation.getContactEmail().trim()).matches()) {
                errors.put("contactEmail", "Please provide a valid email address.");
            }
        }
        if (reservation.getContactPhone() != null && !reservation.getContactPhone().trim().isEmpty()) {
            if (!PHONE_PATTERN.matcher(reservation.getContactPhone().trim()).matches()) {
                errors.put("contactPhone", "Please provide a valid Sri Lankan phone number (e.g. 077 123 4567 or +94771234567).");
            }
        }

        // 6. Validate Promo Discount Coupon
        validateDiscount(discountCode, errors);

        // 7. Validate Add-on Amenities
        validateAddons(serviceIds, facility, errors);

        if (!errors.isEmpty()) {
            throw new ReservationValidationException("Please correct the highlighted errors: " + errors, errors);
        }
    }

    /**
     * Validate arrival & departure times, bounds, and facility operating hours.
     */
    public void validateTimes(LocalDateTime arrival, LocalDateTime departure, ParkingFacility facility, Map<String, String> errors) {
        if (arrival == null) {
            errors.put("arrivalTime", "Arrival date and time is required.");
        }
        if (departure == null) {
            errors.put("departureTime", "Departure date and time is required.");
        }
        if (arrival == null || departure == null) {
            return;
        }

        // Leeway of 5 minutes for user form interaction latency
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        if (arrival.isBefore(fiveMinutesAgo)) {
            errors.put("arrivalTime", "Arrival time cannot be in the past.");
        }

        LocalDateTime maxAdvance = LocalDateTime.now().plusDays(60);
        if (arrival.isAfter(maxAdvance)) {
            errors.put("arrivalTime", "Reservations can only be made up to 60 days in advance.");
        }

        if (!departure.isAfter(arrival)) {
            errors.put("departureTime", "Departure time must be strictly after arrival time.");
            return;
        }

        Duration duration = Duration.between(arrival, departure);
        if (duration.toMinutes() < 30) {
            errors.put("departureTime", "Minimum stay duration is 30 minutes.");
        } else if (duration.toDays() > 30) {
            errors.put("departureTime", "Maximum continuous stay duration cannot exceed 30 days.");
        }

        // Check facility operating hours
        if (facility != null && facility.getOpeningTime() != null && facility.getClosingTime() != null) {
            LocalTime open = facility.getOpeningTime();
            LocalTime close = facility.getClosingTime();
            // Check if facility is not open 24 hours
            boolean is24Hours = open.equals(close) ||
                    (open.getHour() == 0 && open.getMinute() == 0 && close.getHour() == 23 && close.getMinute() >= 59);
            if (!is24Hours) {
                if (open.isBefore(close)) {
                    LocalTime arrTime = arrival.toLocalTime();
                    LocalTime depTime = departure.toLocalTime();
                    if (arrTime.isBefore(open) || arrTime.isAfter(close)) {
                        errors.put("arrivalTime", String.format("Parking complex is closed at the selected arrival time (Operating hours: %s - %s).", open, close));
                    }
                    if (depTime.isBefore(open) || depTime.isAfter(close)) {
                        errors.put("departureTime", String.format("Parking complex is closed at the selected departure time (Operating hours: %s - %s).", open, close));
                    }
                }
            }
        }
    }

    /**
     * Validate vehicle ownership, status, and double-booking overlap.
     */
    public void validateVehicle(Vehicle vehicle, User customer, Long excludeReservationId,
                                LocalDateTime arrival, LocalDateTime departure, Map<String, String> errors) {
        if (vehicle == null || vehicle.getId() == null) {
            errors.put("vehicleId", "Vehicle selection is required.");
            return;
        }

        // Verify customer owns the vehicle (staff/admin bypass)
        if (customer != null && vehicle.getCustomer() != null && !vehicle.getCustomer().getId().equals(customer.getId())) {
            boolean isStaffOrAdmin = customer.getRole() != null &&
                    ("ADMIN".equalsIgnoreCase(customer.getRole().getName()) ||
                     "ROLE_ADMIN".equalsIgnoreCase(customer.getRole().getName()) ||
                     "STAFF".equalsIgnoreCase(customer.getRole().getName()) ||
                     "ROLE_STAFF".equalsIgnoreCase(customer.getRole().getName()));
            if (!isStaffOrAdmin) {
                errors.put("vehicleId", "Unauthorized: The selected vehicle does not belong to your account.");
            }
        }

        if (vehicle.getStatus() == Vehicle.VehicleStatus.INACTIVE) {
            errors.put("vehicleId", "Selected vehicle is marked as inactive.");
        }

        // Check if vehicle has any overlapping active or confirmed reservation across all bays
        if (arrival != null && departure != null && departure.isAfter(arrival)) {
            List<Reservation> conflicts = reservationRepository.findConflictingVehicleReservations(
                    vehicle.getId(), excludeReservationId, arrival, departure
            );
            if (!conflicts.isEmpty()) {
                errors.put("vehicleId", "Vehicle [" + vehicle.getRegistrationNumber() + "] already has an active or confirmed booking during this time window.");
            }
        }
    }

    /**
     * Validate parking space allocation policy, status, and overlap.
     */
    public void validateParkingSpace(ParkingSpace space, boolean isWalkIn, Long excludeReservationId,
                                     LocalDateTime arrival, LocalDateTime departure, Map<String, String> errors) {
        if (space == null || space.getId() == null) {
            errors.put("parkingSpaceId", "Please select a valid parking bay.");
            return;
        }

        if (space.getStatus() == ParkingSpace.ParkingSpaceStatus.MAINTENANCE) {
            errors.put("parkingSpaceId", "Parking bay " + space.getSpaceNumber() + " is currently under maintenance.");
        }

        if (space.getFacility() != null && space.getFacility().getStatus() == ParkingFacility.FacilityStatus.INACTIVE) {
            errors.put("parkingSpaceId", "Parking facility '" + space.getFacility().getName() + "' is currently inactive and not available for booking.");
        }



        // Mathematical interval overlap check with turnover buffer
        if (arrival != null && departure != null && departure.isAfter(arrival)) {
            LocalDateTime bufferArrival = arrival.minusMinutes(TURNOVER_BUFFER_MINUTES);
            LocalDateTime bufferDeparture = departure.plusMinutes(TURNOVER_BUFFER_MINUTES);
            List<Reservation> conflicts = reservationRepository.findConflictingReservationsWithExclusion(
                    space.getId(), excludeReservationId, bufferArrival, bufferDeparture
            );
            if (!conflicts.isEmpty()) {
                errors.put("parkingSpaceId", "Parking bay " + space.getSpaceNumber() + " requires a " + TURNOVER_BUFFER_MINUTES + "-minute turnover buffer and is unavailable during this window. Please choose another bay or time.");
            }
        }
    }

    /**
     * Validate promo discount coupon.
     */
    public void validateDiscount(String discountCode, Map<String, String> errors) {
        if (discountCode == null || discountCode.trim().isEmpty()) {
            return;
        }
        String cleanCode = discountCode.trim().toUpperCase();
        if (!discountService.isValidDiscount(cleanCode)) {
            String reason = discountService.getDiscountValidationMessage(cleanCode);
            errors.put("discountCode", reason != null ? reason : "This discount code cannot be applied.");
        }
    }

    /**
     * Validate add-on amenities belong to the target facility and are available.
     */
    public void validateAddons(List<Long> serviceIds, ParkingFacility facility, Map<String, String> errors) {
        if (serviceIds == null || serviceIds.isEmpty() || facilityServiceRepository == null) {
            return;
        }

        for (Long sId : serviceIds) {
            if (sId == null) continue;
            FacilityService srv = facilityServiceRepository.findById(sId).orElse(null);
            if (srv == null) {
                errors.put("serviceIds", "One or more selected add-on amenities do not exist.");
                break;
            }
            if (!srv.isAvailable()) {
                errors.put("serviceIds", "Selected amenity '" + srv.getName() + "' is currently unavailable.");
                break;
            }
            if (facility != null && facility.getId() != null && !srv.belongsToFacility(facility.getId())) {
                errors.put("serviceIds", "Selected amenity '" + srv.getName() + "' does not belong to the selected parking complex.");
                break;
            }
        }
    }

    /**
     * Validate walk-in pass creation.
     */
    public void validateForWalkIn(ParkingSpace space, String licensePlate, String vehicleType, Integer hours,
                                   String guestName, String contactPhone, User staffUser) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (!com.example.parkngo.vehicle.Vehicle.isSupportedType(vehicleType)) {
            errors.put("vehicleType", "Please select a supported vehicle type.");
        }

        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            errors.put("licensePlate", "License plate number is required for walk-in pass.");
        } else if (!PLATE_PATTERN.matcher(licensePlate.trim().toUpperCase()).matches()) {
            errors.put("licensePlate", "Please enter a valid Sri Lankan license plate (e.g. WP CA-1234, CAB-5678, or 19-1234).");
        }

        if (contactPhone != null && !contactPhone.trim().isEmpty()
                && !PHONE_PATTERN.matcher(contactPhone.trim()).matches()) {
            errors.put("contactPhone", "Please enter a valid Sri Lankan phone number (e.g. 077 123 4567 or +94771234567).");
        }

        if (space == null || space.getId() == null) {
            errors.put("parkingSpaceId", "Please select an available parking bay.");
        } else {
            if (space.getStatus() == ParkingSpace.ParkingSpaceStatus.MAINTENANCE ||
                space.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED ||
                space.getStatus() == ParkingSpace.ParkingSpaceStatus.OUT_OF_SERVICE) {
                errors.put("parkingSpaceId", "Selected parking bay " + space.getSpaceNumber() + " is currently not available for walk-in.");
            }

        }

        if (hours == null || hours < 1 || hours > 24) {
            errors.put("durationHours", "Walk-in stay duration must be between 1 and 24 hours.");
        }

        if (!errors.isEmpty()) {
            throw new ReservationValidationException(String.join(" ", errors.values()), errors);
        }
    }

    /**
     * Validate gate check-in state transitions.
     */
    public void validateForCheckIn(Reservation res) {
        if (res == null) {
            throw new ReservationValidationException("Reservation record not found.");
        }

        if (res.getStatus() == Reservation.ReservationStatus.ACTIVE) {
            throw new ReservationValidationException("Vehicle is already checked-in and inside the garage.");
        }

        if (res.getStatus() == Reservation.ReservationStatus.COMPLETED ||
            res.getStatus() == Reservation.ReservationStatus.CANCELLED ||
            res.getStatus() == Reservation.ReservationStatus.EXPIRED) {
            throw new ReservationValidationException("Cannot check in a " + res.getStatus() + " reservation.");
        }

        LocalDateTime now = LocalDateTime.now();

        // Prevent check-in more than 15 minutes before scheduled arrival
        if (res.getArrivalTime() != null && now.isBefore(res.getArrivalTime().minusMinutes(EARLY_CHECKIN_MINUTES))) {
            throw new ReservationValidationException("Early check-in not permitted. You may check in at most " + EARLY_CHECKIN_MINUTES + " minutes prior to scheduled arrival.");
        }

        // Prevent check-in if scheduled departure time has already passed
        if (res.getDepartureTime() != null && now.isAfter(res.getDepartureTime())) {
            throw new ReservationValidationException("Reservation has expired. The scheduled departure time has already passed.");
        }
    }

    /**
     * Validate gate check-out state transitions.
     */
    public void validateForCheckOut(Reservation res) {
        if (res == null) {
            throw new ReservationValidationException("Reservation record not found.");
        }

        if (res.getStatus() != Reservation.ReservationStatus.ACTIVE
                && res.getStatus() != Reservation.ReservationStatus.OVERSTAYING) {
            throw new ReservationValidationException("Only active parked vehicles can be checked out.");
        }
    }

    /**
     * Validate reservation cancellation permissions and state.
     */
    public void validateForCancellation(Reservation res, User currentUser) {
        if (res == null) {
            throw new ReservationValidationException("Reservation record not found.");
        }

        if (res.getStatus() == Reservation.ReservationStatus.ACTIVE
                || res.getStatus() == Reservation.ReservationStatus.OVERSTAYING
                || res.getStatus() == Reservation.ReservationStatus.OVERSTAY_PAYMENT_DUE) {
            throw new ReservationValidationException("Cannot cancel a vehicle that is still inside the facility. Please settle checkout at the gate.");
        }

        if (res.getStatus() == Reservation.ReservationStatus.COMPLETED) {
            throw new ReservationValidationException("Completed reservations cannot be cancelled.");
        }

        if (res.getStatus() == Reservation.ReservationStatus.CANCELLED) {
            throw new ReservationValidationException("Reservation is already cancelled.");
        }

        if (res.getStatus() == Reservation.ReservationStatus.EXPIRED) {
            throw new ReservationValidationException("Expired reservations cannot be cancelled.");
        }

        // Authorization check: Customer cannot cancel another customer's booking
        boolean isStaffOrAdmin = currentUser != null && currentUser.getRole() != null &&
                ("ADMIN".equalsIgnoreCase(currentUser.getRole().getName()) ||
                 "ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole().getName()) ||
                 "STAFF".equalsIgnoreCase(currentUser.getRole().getName()) ||
                 "ROLE_STAFF".equalsIgnoreCase(currentUser.getRole().getName()));

        if (currentUser != null && res.getCustomer() != null && !res.getCustomer().getId().equals(currentUser.getId())) {
            if (!isStaffOrAdmin) {
                throw new ReservationValidationException("Unauthorized: You can only cancel your own reservations.");
            }
        }

        // Temporal cutoff: cannot cancel after scheduled arrival time has passed (unless staff/admin)
        if (!isStaffOrAdmin && res.getArrivalTime() != null && LocalDateTime.now().isAfter(res.getArrivalTime())) {
            throw new ReservationValidationException("Cannot cancel reservation after the scheduled arrival time has passed.");
        }
    }

    /**
     * Validate reservation edit permissions, time cutoff, and state.
     */
    public void validateForEdit(Reservation res, User currentUser) {
        if (res == null) {
            throw new ReservationValidationException("Reservation record not found.");
        }

        if (res.getStatus() != Reservation.ReservationStatus.PENDING &&
            res.getStatus() != Reservation.ReservationStatus.CONFIRMED) {
            throw new ReservationValidationException("Only pending or confirmed reservations can be edited.");
        }

        boolean isStaffOrAdmin = currentUser != null && currentUser.getRole() != null &&
                ("ADMIN".equalsIgnoreCase(currentUser.getRole().getName()) ||
                 "ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole().getName()) ||
                 "STAFF".equalsIgnoreCase(currentUser.getRole().getName()) ||
                 "ROLE_STAFF".equalsIgnoreCase(currentUser.getRole().getName()));

        if (currentUser != null && res.getCustomer() != null && !res.getCustomer().getId().equals(currentUser.getId())) {
            if (!isStaffOrAdmin) {
                throw new ReservationValidationException("Unauthorized: You can only edit your own reservations.");
            }
        }

        if (!isStaffOrAdmin && res.getArrivalTime() != null && LocalDateTime.now().isAfter(res.getArrivalTime())) {
            throw new ReservationValidationException("Reservations cannot be edited after the scheduled arrival time has passed.");
        }
    }
}
