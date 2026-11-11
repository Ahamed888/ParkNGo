package com.example.parkngo.reservation;

import com.example.parkngo.discount.Discount;
import com.example.parkngo.discount.DiscountService;
import com.example.parkngo.facility.FacilityService;
import com.example.parkngo.facility.FacilityServiceRepository;
import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityService;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceService;
import com.example.parkngo.subscription.Subscription;
import com.example.parkngo.subscription.SubscriptionService;
import com.example.parkngo.user.User;
import com.example.parkngo.util.SecurityUtil;
import com.example.parkngo.vehicle.Vehicle;
import com.example.parkngo.vehicle.VehicleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.example.parkngo.payment.Payment;
import com.example.parkngo.payment.PaymentRepository;
import com.example.parkngo.payment.PaymentService;
import com.example.parkngo.staff.StaffFacilityAccessService;

@Controller
public class ReservationController {
    private final ReservationService reservationService;
    private final VehicleService vehicleService;
    private final ParkingFacilityService facilityService;
    private final ParkingSpaceService spaceService;
    private final FacilityServiceRepository facilityServiceRepository;
    private final DiscountService discountService;
    private final SecurityUtil securityUtil;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;
    private final StaffFacilityAccessService staffFacilityAccess;

    public ReservationController(ReservationService reservationService,
                                 VehicleService vehicleService,
                                 ParkingFacilityService facilityService,
                                 ParkingSpaceService spaceService,
                                 FacilityServiceRepository facilityServiceRepository,
                                 DiscountService discountService,
                                 SecurityUtil securityUtil,
                                 PaymentRepository paymentRepository,
                                 PaymentService paymentService,
                                 SubscriptionService subscriptionService,
                                 StaffFacilityAccessService staffFacilityAccess) {
        this.reservationService = reservationService;
        this.vehicleService = vehicleService;
        this.facilityService = facilityService;
        this.spaceService = spaceService;
        this.facilityServiceRepository = facilityServiceRepository;
        this.discountService = discountService;
        this.securityUtil = securityUtil;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.subscriptionService = subscriptionService;
        this.staffFacilityAccess = staffFacilityAccess;
    }

    // ==========================================
    // CUSTOMER RESERVATION WORKFLOWS
    // ==========================================

    @GetMapping("/customer/reservation/create")
    public String createReservationForm(@RequestParam(required = false) Long facilityId,
                                        @RequestParam(required = false) Long spaceId,
                                        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
                                        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.TIME) java.time.LocalTime time,
                                        Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        try {
            Reservation reservation = new Reservation();
            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
            if (date != null && time != null) {
                LocalDateTime arrival = date.atTime(time.withSecond(0).withNano(0));
                if (!arrival.isBefore(now)) {
                    reservation.setArrivalTime(arrival);
                    reservation.setDepartureTime(arrival.plusHours(2));
                } else {
                    reservation.setArrivalTime(now);
                    reservation.setDepartureTime(now.plusHours(2));
                }
            } else {
                reservation.setArrivalTime(now);
                reservation.setDepartureTime(now.plusHours(2));
            }

            if (facilityId == null && spaceId != null) {
                try {
                    ParkingSpace ps = spaceService.getParkingSpaceById(spaceId);
                    if (ps != null && ps.getFacility() != null) {
                        facilityId = ps.getFacility().getId();
                    }
                } catch (Exception ignored) {}
            }

            List<ParkingFacility> facilities = facilityService.getAllFacilities();
            if (facilityId != null) {
                final Long fid = facilityId;
                boolean isActive = facilities.stream().anyMatch(f -> f.getId().equals(fid));
                if (!isActive) {
                    facilityId = (!facilities.isEmpty()) ? facilities.get(0).getId() : null;
                }
            } else if (facilities != null && !facilities.isEmpty()) {
                facilityId = facilities.get(0).getId();
            }

            populateFormModel(model, user, reservation, null, spaceId);
            model.addAttribute("selectedFacilityId", facilityId);
            model.addAttribute("mode", "create");
            return "customer/reservation-create";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Unable to load reservation page: " + e.getMessage());
            return "customer/dashboard";
        }
    }

    @GetMapping("/customer/reservation/available-bays")
    @ResponseBody
    public List<java.util.Map<String, Object>> getAvailableBays(
            @RequestParam Long facilityId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrivalTime,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureTime) {
        List<ParkingSpace> spaces = reservationService.getAvailableSpacesForWindow(facilityId, arrivalTime, departureTime);
        return spaces.stream().map(s -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", s.getId());
            map.put("spaceNumber", s.getSpaceNumber());
            map.put("floor", s.getFloor());
            map.put("zone", s.getZone());
            map.put("parkingType", s.getParkingType());
            map.put("hourlyRate", s.getHourlyRate());
            map.put("hasEVCharging", s.getHasEVCharging());
            map.put("isAccessible", s.getIsAccessible());
            return map;
        }).toList();
    }

    @PostMapping("/customer/reservation/create")
    public String createReservation(@ModelAttribute Reservation reservation,
                                    @RequestParam(required = false) Long vehicleId,
                                    @RequestParam(required = false) String quickPlate,
                                    @RequestParam(required = false) String quickType,
                                    @RequestParam(required = false) String quickBrand,
                                    @RequestParam(required = false) String quickModel,
                                    @RequestParam(required = false) Long facilityId,
                                    @RequestParam(required = false) Long parkingSpaceId,
                                    @RequestParam(required = false) Long spaceId,
                                    @RequestParam(required = false) List<Long> serviceIds,
                                    @RequestParam(required = false) String discountCode,
                                    @RequestParam(required = false, defaultValue = "CREDIT_CARD") String paymentMethod,
                                    @RequestParam(required = false) String cardNumber,
                                    @RequestParam(required = false) String cardholderName,
                                    @RequestParam(required = false) String expiryDate,
                                    @RequestParam(required = false) String cvc,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        Long targetSpaceId = parkingSpaceId != null ? parkingSpaceId : spaceId;
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return "redirect:/login";

            if (facilityId == null && targetSpaceId == null) {
                throw new ReservationValidationException(
                        "Parking complex is required",
                        "facilityId",
                        "Please select a specific parking complex / garage."
                );
            }

            if (facilityId != null) {
                try {
                    ParkingFacility selectedFac = facilityService.getFacilityById(facilityId);
                    if (selectedFac.getStatus() != ParkingFacility.FacilityStatus.ACTIVE) {
                        throw new ReservationValidationException(
                                "Facility is inactive",
                                "facilityId",
                                "The selected parking facility is currently inactive and not available for booking."
                        );
                    }
                } catch (ReservationValidationException rve) {
                    throw rve;
                } catch (Exception ignored) {}
            }

            Vehicle vehicle = null;
            if ((vehicleId == null || vehicleId <= 0) && quickPlate != null && !quickPlate.trim().isEmpty()) {
                String cleanPlate = quickPlate.trim().toUpperCase();
                if (!com.example.parkngo.util.SriLankanPlateValidator.isValid(cleanPlate)) {
                    throw new ReservationValidationException(
                            "Invalid Sri Lankan plate number",
                            "vehicleId",
                            "Please enter a valid Sri Lankan license plate number (e.g. WP CA-1234, CAB-5678, or 19-1234)."
                    );
                }
                if (!Vehicle.isSupportedType(quickType)) {
                    throw new ReservationValidationException(
                            "Unsupported vehicle type",
                            "vehicleId",
                            "Please select a supported vehicle type."
                    );
                }
                Vehicle newVehicle = new Vehicle();
                newVehicle.setRegistrationNumber(com.example.parkngo.util.SriLankanPlateValidator.normalize(cleanPlate));
                newVehicle.setVehicleType(quickType != null && !quickType.isEmpty() ? quickType : "SEDAN");
                newVehicle.setBrand(quickBrand != null && !quickBrand.trim().isEmpty() ? quickBrand.trim() : "Standard");
                newVehicle.setModel(quickModel != null && !quickModel.trim().isEmpty() ? quickModel.trim() : "Car");
                newVehicle.setColor("Black");
                newVehicle.setCustomer(user);
                vehicle = vehicleService.registerVehicle(newVehicle);
            } else if (vehicleId != null && vehicleId > 0) {
                try {
                    vehicle = vehicleService.getVehicleById(vehicleId);
                } catch (Exception ignored) {}
            }

            if (vehicle == null) {
                throw new ReservationValidationException(
                        "Vehicle selection is required",
                        "vehicleId",
                        "Please select a registered vehicle or enter your license plate."
                );
            }

            if (targetSpaceId == null) {
                throw new ReservationValidationException(
                        "Parking bay is required",
                        "parkingSpaceId",
                        "Please select an available parking bay."
                );
            }

            ParkingSpace space;
            try {
                space = spaceService.getParkingSpaceById(targetSpaceId);
            } catch (Exception ex) {
                throw new ReservationValidationException(
                        "Parking bay not found",
                        "parkingSpaceId",
                        "The selected parking bay does not exist or has been removed."
                );
            }

            if (facilityId != null && space.getFacility() != null && !space.getFacility().getId().equals(facilityId)) {
                throw new ReservationValidationException(
                        "Bay does not belong to parking complex",
                        "parkingSpaceId",
                        "The selected parking bay does not belong to the chosen parking complex."
                );
            }

            if (space.getFacility() != null && space.getFacility().getStatus() != ParkingFacility.FacilityStatus.ACTIVE) {
                throw new ReservationValidationException(
                        "Facility is inactive",
                        "parkingSpaceId",
                        "The parking facility for this bay is currently inactive and not available for booking."
                );
            }

            reservation.setCustomer(user);
            reservation.setVehicle(vehicle);
            reservation.setParkingSpace(space);

            // Integrated Book & Pay in a single atomic transaction
            Reservation saved = reservationService.bookAndPayReservation(
                    reservation, serviceIds, discountCode, paymentMethod, cardNumber, cardholderName, expiryDate, cvc, user
            );
            redirectAttributes.addFlashAttribute("successMessage", "Reservation confirmed for vehicle " + saved.getVehicle().getRegistrationNumber() + "! Bay " + saved.getParkingSpace().getSpaceNumber() + " is reserved.");
            return "redirect:/customer/reservation/" + saved.getId();
        } catch (ReservationValidationException e) {
            User user = securityUtil.getCurrentUser();
            populateFormModel(model, user, reservation, vehicleId, targetSpaceId);
            model.addAttribute("selectedFacilityId", facilityId);
            model.addAttribute("prevQuickPlate", quickPlate);
            model.addAttribute("prevQuickType", quickType);
            model.addAttribute("prevQuickBrand", quickBrand);
            model.addAttribute("prevQuickModel", quickModel);
            model.addAttribute("prevServiceIds", serviceIds);
            model.addAttribute("prevDiscountCode", discountCode);
            model.addAttribute("prevPaymentMethod", paymentMethod);
            model.addAttribute("prevCardholderName", cardholderName);
            model.addAttribute("prevCardNumber", cardNumber);
            model.addAttribute("prevExpiryDate", expiryDate);
            model.addAttribute("fieldErrors", e.getFieldErrors());
            model.addAttribute("errorMessage", e.getMessage());
            return "customer/reservation-create";
        } catch (Exception e) {
            User user = securityUtil.getCurrentUser();
            populateFormModel(model, user, reservation, vehicleId, targetSpaceId);
            model.addAttribute("selectedFacilityId", facilityId);
            model.addAttribute("prevQuickPlate", quickPlate);
            model.addAttribute("prevQuickType", quickType);
            model.addAttribute("prevQuickBrand", quickBrand);
            model.addAttribute("prevQuickModel", quickModel);
            model.addAttribute("prevServiceIds", serviceIds);
            model.addAttribute("prevDiscountCode", discountCode);
            model.addAttribute("prevPaymentMethod", paymentMethod);
            model.addAttribute("prevCardholderName", cardholderName);
            model.addAttribute("prevCardNumber", cardNumber);
            model.addAttribute("prevExpiryDate", expiryDate);
            model.addAttribute("errorMessage", "Booking failed: " + e.getMessage());
            return "customer/reservation-create";
        }
    }

    private void populateFormModel(Model model, User user, Reservation reservation, Long selectedVehicleId, Long selectedSpaceId) {
        List<Vehicle> vehicles = (user != null) ? vehicleService.getCustomerVehicles(user.getId()) : List.of();
        List<ParkingFacility> facilities = facilityService.getAllFacilities();
        List<ParkingSpace> spaces = spaceService.getAllParkingSpaces().stream()
                .filter(space -> space.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE)
                .filter(space -> space.getFacility() != null && space.getFacility().getStatus() == ParkingFacility.FacilityStatus.ACTIVE)
                .toList();
        List<FacilityService> availableServices = facilityServiceRepository.findAll().stream()
                .filter(s -> s != null && s.isAvailable())
                .toList();
        List<Discount> activeDiscounts = discountService.getActiveDiscounts();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        if (reservation.getArrivalTime() != null) {
            model.addAttribute("defaultArrival", reservation.getArrivalTime().format(dtf));
        } else {
            model.addAttribute("defaultArrival", LocalDateTime.now().format(dtf));
        }
        if (reservation.getDepartureTime() != null) {
            model.addAttribute("defaultDeparture", reservation.getDepartureTime().format(dtf));
        } else {
            model.addAttribute("defaultDeparture", LocalDateTime.now().plusHours(2).format(dtf));
        }

        model.addAttribute("vehicles", vehicles);
        model.addAttribute("facilities", facilities);
        model.addAttribute("spaces", spaces);
        model.addAttribute("availableServices", availableServices);
        model.addAttribute("selectedVehicleId", selectedVehicleId);
        model.addAttribute("selectedSpaceId", selectedSpaceId);
        model.addAttribute("discounts", activeDiscounts);
        model.addAttribute("reservation", reservation);
        Long facilityId = selectedSpaceId != null ? spaces.stream().filter(space -> selectedSpaceId.equals(space.getId()))
                .map(space -> space.getFacility().getId()).findFirst().orElse(null) : null;
        List<Subscription> parkingMemberships = user != null ? subscriptionService.getParkingMemberships(user.getId()) : List.of();
        java.util.Map<Long, java.util.Map<String, Object>> membershipPricing = new java.util.LinkedHashMap<>();
        for (ParkingFacility facility : facilities) {
            parkingMemberships.stream().filter(pass -> pass.getMembershipPackage().isEligibleForFacility(facility.getId()))
                    .findFirst().ifPresent(pass -> membershipPricing.put(facility.getId(), java.util.Map.of(
                            "percent", pass.getMembershipPackage().getParkingDiscountPercentage(),
                            "name", pass.getPackageNameSnap() != null ? pass.getPackageNameSnap() : pass.getMembershipPackage().getName())));
        }
        model.addAttribute("membershipPricing", membershipPricing);
        Subscription membership = parkingMemberships.stream()
                .filter(pass -> facilityId != null && pass.getMembershipPackage().isEligibleForFacility(facilityId))
                .findFirst().orElse(null);
        if (membership != null) {
            model.addAttribute("activeMembershipName", membership.getPackageNameSnap() != null ? membership.getPackageNameSnap() : membership.getMembershipPackage().getName());
            model.addAttribute("activeMembershipDiscountPercent", membership.getMembershipPackage().getParkingDiscountPercentage());
            model.addAttribute("membershipBenefits", membership.getMembershipPackage().getBenefits());
        }
    }

    @GetMapping("/customer/reservations")
    public String viewCustomerReservations(Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        List<Reservation> reservations = reservationService.getCustomerReservations(user.getId());
        model.addAttribute("reservations", reservations);
        return "customer/reservations";
    }

    @GetMapping("/customer/reservation/{id}/edit")
    public String editReservationForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";
        try {
            Reservation reservation = reservationService.getReservationById(id);
            if (reservation.getCustomer() == null || !user.getId().equals(reservation.getCustomer().getId())
                    || (reservation.getStatus() != Reservation.ReservationStatus.PENDING
                    && reservation.getStatus() != Reservation.ReservationStatus.CONFIRMED)) {
                throw new ReservationValidationException("Only your pending or confirmed reservations can be edited.");
            }
            populateFormModel(model, user, reservation,
                    reservation.getVehicle().getId(), reservation.getParkingSpace().getId());
            model.addAttribute("selectedFacilityId", reservation.getParkingSpace().getFacility().getId());
            model.addAttribute("mode", "edit");

            List<Long> initialServiceIds = reservation.getServiceItems() != null
                    ? reservation.getServiceItems().stream()
                        .map(it -> it.getService() != null ? it.getService().getId() : null)
                        .filter(java.util.Objects::nonNull)
                        .toList()
                    : List.of();
            model.addAttribute("prevServiceIds", initialServiceIds);

            java.math.BigDecimal paidAmount = paymentService.getPaidAmountForReservation(id);
            java.math.BigDecimal refundedAmount = paymentService.getRefundedAmountForReservation(id);
            List<Payment> pList = paymentRepository.findByReservationId(id);
            boolean isPayAtGate = pList.stream().anyMatch(p -> p.getPaymentMethod() == Payment.PaymentMethod.CASH_ON_ENTRY && p.getStatus() == Payment.PaymentStatus.PENDING);
            Payment cardPayment = pList.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.PAID).findFirst().orElse(null);

            model.addAttribute("originalPaidAmount", paidAmount);
            model.addAttribute("originalRefundedAmount", refundedAmount);
            model.addAttribute("isPayAtGate", isPayAtGate);
            model.addAttribute("originalTotal", reservation.getTotalAmount());
            if (cardPayment != null) {
                model.addAttribute("originalCardBrand", cardPayment.getCardBrand());
                model.addAttribute("originalMaskedCard", cardPayment.getMaskedCardNumber());
            }
            return "customer/reservation-create";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/customer/reservations";
        }
    }

    @PostMapping("/customer/reservation/edit/{id}")
    public String editReservation(@PathVariable Long id,
                                  @ModelAttribute Reservation reservation,
                                  @RequestParam(required = false) Long parkingSpaceId,
                                  @RequestParam(required = false) Long vehicleId,
                                  @RequestParam(required = false) List<Long> serviceIds,
                                  @RequestParam(required = false, defaultValue = "CREDIT_CARD") String paymentMethod,
                                  @RequestParam(required = false) String cardNumber,
                                  @RequestParam(required = false) String cardholderName,
                                  @RequestParam(required = false) String expiryDate,
                                  @RequestParam(required = false) String cvc,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";
        List<Long> effectiveServices = serviceIds != null ? serviceIds : java.util.Collections.emptyList();
        try {
            Reservation saved = reservationService.updateReservationSchedule(
                    id, reservation.getArrivalTime(), reservation.getDepartureTime(), parkingSpaceId, vehicleId,
                    effectiveServices, paymentMethod, cardNumber, cardholderName, expiryDate, cvc, user);
            redirectAttributes.addFlashAttribute("successMessage", "Reservation updated successfully.");
            return "redirect:/customer/reservation/" + saved.getId();
        } catch (ReservationValidationException e) {
            try {
                Reservation existing = reservationService.getReservationById(id);
                populateFormModel(model, user, reservation,
                        (vehicleId != null ? vehicleId : existing.getVehicle().getId()),
                        (parkingSpaceId != null ? parkingSpaceId : existing.getParkingSpace().getId()));
                model.addAttribute("selectedFacilityId", existing.getParkingSpace().getFacility().getId());

                java.math.BigDecimal paidAmount = paymentService.getPaidAmountForReservation(id);
                java.math.BigDecimal refundedAmount = paymentService.getRefundedAmountForReservation(id);
                List<Payment> pList = paymentRepository.findByReservationId(id);
                boolean isPayAtGate = pList.stream().anyMatch(p -> p.getPaymentMethod() == Payment.PaymentMethod.CASH_ON_ENTRY && p.getStatus() == Payment.PaymentStatus.PENDING);
                Payment cardPayment = pList.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.PAID).findFirst().orElse(null);

                model.addAttribute("originalPaidAmount", paidAmount);
                model.addAttribute("originalRefundedAmount", refundedAmount);
                model.addAttribute("isPayAtGate", isPayAtGate);
                model.addAttribute("originalTotal", existing.getTotalAmount());
                if (cardPayment != null) {
                    model.addAttribute("originalCardBrand", cardPayment.getCardBrand());
                    model.addAttribute("originalMaskedCard", cardPayment.getMaskedCardNumber());
                }
            } catch (Exception ignored) {
            }
            model.addAttribute("mode", "edit");
            model.addAttribute("prevServiceIds", effectiveServices);
            model.addAttribute("prevPaymentMethod", paymentMethod);
            model.addAttribute("prevCardholderName", cardholderName);
            model.addAttribute("prevCardNumber", cardNumber);
            model.addAttribute("prevExpiryDate", expiryDate);
            model.addAttribute("fieldErrors", e.getFieldErrors());
            model.addAttribute("errorMessage", e.getMessage());
            return "customer/reservation-create";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to update reservation: " + e.getMessage());
            return "redirect:/customer/reservations";
        }
    }

    @GetMapping("/customer/reservation/{id}")
    public String viewReservationDetails(@PathVariable Long id, Model model) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return "redirect:/login";
            Reservation reservation = reservationService.getReservationById(id);
            if (reservation.getCustomer() == null || !user.getId().equals(reservation.getCustomer().getId())) {
                throw new ReservationValidationException("Reservation not found");
            }
            java.math.BigDecimal paidAmount = paymentService.getPaidAmountForReservation(id);
            java.math.BigDecimal refundedAmount = paymentService.getRefundedAmountForReservation(id);
            java.math.BigDecimal displayedPaidAmount = paidAmount != null
                    && paidAmount.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? paidAmount
                    : reservation.getTotalAmount();
            boolean hasRefundAmount = refundedAmount != null
                    && refundedAmount.compareTo(java.math.BigDecimal.ZERO) > 0;
            java.math.BigDecimal displayedRefundAmount = hasRefundAmount
                    ? refundedAmount
                    : (paidAmount != null ? paidAmount : reservation.getTotalAmount());
            List<Payment> pList = paymentRepository.findByReservationId(id);
            Payment primaryPayment = pList.isEmpty() ? null : pList.get(0);
            Payment cardPayment = pList.stream().filter(p -> p.getPaymentMethod() == Payment.PaymentMethod.CREDIT_CARD || p.getPaymentMethod() == Payment.PaymentMethod.DEBIT_CARD).findFirst().orElse(null);
            boolean isPayAtGate = pList.stream().anyMatch(p -> p.getPaymentMethod() == Payment.PaymentMethod.CASH_ON_ENTRY);

            model.addAttribute("reservation", reservation);
            model.addAttribute("paidAmount", paidAmount);
            model.addAttribute("displayedPaidAmount", displayedPaidAmount);
            model.addAttribute("hasRefundAmount", hasRefundAmount);
            model.addAttribute("displayedRefundAmount", displayedRefundAmount);
            model.addAttribute("refundedAmount", refundedAmount);
            model.addAttribute("primaryPayment", primaryPayment);
            model.addAttribute("cardPayment", cardPayment);
            model.addAttribute("isPayAtGate", isPayAtGate);
            return "customer/reservation-details";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Reservation not found");
            return "redirect:/customer/reservations";
        }
    }

    @PostMapping("/customer/reservation/{id}/cancel")
    public String cancelReservation(@PathVariable Long id,
                                    @RequestHeader(value = "Referer", required = false) String referer,
                                    RedirectAttributes redirectAttributes) {
        try {
            User user = securityUtil.getCurrentUser();
            java.math.BigDecimal paidAmount = paymentService.getPaidAmountForReservation(id);
            reservationService.cancelReservation(id, user);
            String msg = paidAmount.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? "Reservation cancelled successfully. A 100% full refund of LKR " + paidAmount + " has been processed to your card."
                    : "Reservation cancelled successfully. No charges were incurred.";
            redirectAttributes.addFlashAttribute("success", msg);
            redirectAttributes.addFlashAttribute("successMessage", msg);

            if (referer != null && referer.contains("/customer/reservation/" + id)) {
                return "redirect:/customer/reservation/" + id;
            }
            return "redirect:/customer/reservations";
        } catch (Exception e) {
            String err = "Cancellation failed: " + e.getMessage();
            redirectAttributes.addFlashAttribute("error", err);
            redirectAttributes.addFlashAttribute("errorMessage", err);
            return "redirect:/customer/reservations";
        }
    }

    // ==========================================
    // STAFF WALK-IN SYSTEM
    // ==========================================

    @GetMapping("/staff/walk-in")
    public String showWalkInForm(Model model) {
        populateWalkInModel(model);
        return "staff/walk-in";
    }

    private void populateWalkInModel(Model model) {
        ParkingFacility assignedFacility = staffFacilityAccess.currentAssignedFacility();
        List<ParkingSpace> availableSpaces = spaceService.getFacilityParkingSpaces(assignedFacility.getId()).stream()
                .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE)
                .filter(s -> s.getAllocationType() == null || !"ONLINE_BOOKABLE".equalsIgnoreCase(s.getAllocationType()))
                .toList();
        List<ParkingFacility> facilities = List.of(assignedFacility);

        model.addAttribute("availableSpaces", availableSpaces);
        model.addAttribute("facilities", facilities);
        model.addAttribute("assignedFacility", assignedFacility);
        model.addAttribute("walkInMap", true);
        model.addAttribute("allFloors", true);
    }

    @PostMapping("/staff/walk-in")
    public String processWalkIn(@RequestParam Long parkingSpaceId,
                                @RequestParam String licensePlate,
                                @RequestParam(defaultValue = "CAR") String vehicleType,
                                @RequestParam(required = false) String brand,
                                @RequestParam(required = false) String modelName,
                                @RequestParam(required = false, defaultValue = "Unknown") String color,
                                @RequestParam(required = false) String guestName,
                                @RequestParam(required = false) String contactPhone,
                                @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrivalTime,
                                @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureTime,
                                @RequestParam(required = false, defaultValue = "2") Integer durationHours,
                                RedirectAttributes redirectAttributes) {
        try {
            User staffUser = securityUtil.getCurrentUser();
            Reservation walkInPass = reservationService.createWalkInReservation(
                    parkingSpaceId, licensePlate, vehicleType, brand, modelName, color, guestName, contactPhone, arrivalTime, departureTime, durationHours, staffUser);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Walk-in entry granted for vehicle "
                            + (walkInPass instanceof WalkInReservation w ? w.getGuestVehicleRegNumber() : licensePlate) + "!");
            return "redirect:/staff/active-reservations";
        } catch (ReservationValidationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("fieldErrors", e.getFieldErrors());
            redirectAttributes.addFlashAttribute("prevLicensePlate", licensePlate);
            redirectAttributes.addFlashAttribute("prevVehicleType", vehicleType);
            redirectAttributes.addFlashAttribute("prevDurationHours", durationHours);
            return "redirect:/staff/walk-in";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to issue walk-in pass: " + e.getMessage());
            redirectAttributes.addFlashAttribute("prevLicensePlate", licensePlate);
            redirectAttributes.addFlashAttribute("prevVehicleType", vehicleType);
            redirectAttributes.addFlashAttribute("prevDurationHours", durationHours);
            return "redirect:/staff/walk-in";
        }
    }

    // ==========================================
    // STAFF ARRIVAL VERIFICATION & CHECK-IN/OUT
    // ==========================================

    @GetMapping("/staff/verify-reservation")
    public String verifyReservationPage(@RequestParam(required = false) String search, Model model) {
        // Today's upcoming arrivals for fast gate queue
        LocalDateTime now = LocalDateTime.now();
        Long facilityId = staffFacilityAccess.currentAssignedFacility().getId();
        List<Reservation> allReservations = reservationService.getAllReservations();
        List<Reservation> expectedArrivals = allReservations.stream()
                .filter(r -> belongsToFacility(r, facilityId))
                .filter(r -> r.getStatus() == Reservation.ReservationStatus.CONFIRMED || r.getStatus() == Reservation.ReservationStatus.PENDING)
                .sorted((r1, r2) -> {
                    if (r1.getArrivalTime() == null) return 1;
                    if (r2.getArrivalTime() == null) return -1;
                    return r1.getArrivalTime().compareTo(r2.getArrivalTime());
                })
                .limit(20)
                .toList();
        model.addAttribute("expectedArrivals", expectedArrivals);

        // Fetch payments mapping for reservations to display payment status
        java.util.Map<Long, Payment> paymentMap = new java.util.HashMap<>();
        for (Reservation r : expectedArrivals) {
            List<Payment> pList = paymentRepository.findByReservationId(r.getId());
            if (!pList.isEmpty()) {
                paymentMap.put(r.getId(), pList.get(0));
            }
        }

        if (search != null && !search.trim().isEmpty()) {
            List<Reservation> results = reservationService.searchReservations(search.trim()).stream()
                    .filter(r -> belongsToFacility(r, facilityId))
                    .filter(r -> r.getStatus() == Reservation.ReservationStatus.CONFIRMED 
                              || r.getStatus() == Reservation.ReservationStatus.PENDING 
                              || r.getStatus() == Reservation.ReservationStatus.ACTIVE
                              || r.getStatus() == Reservation.ReservationStatus.OVERSTAYING
                              || r.getStatus() == Reservation.ReservationStatus.OVERSTAY_PAYMENT_DUE)
                    .toList();
            model.addAttribute("searchResults", results);
            model.addAttribute("searchTerm", search.trim());

            for (Reservation r : results) {
                if (!paymentMap.containsKey(r.getId())) {
                    List<Payment> pList = paymentRepository.findByReservationId(r.getId());
                    if (!pList.isEmpty()) {
                        paymentMap.put(r.getId(), pList.get(0));
                    }
                }
            }
        }
        model.addAttribute("paymentMap", paymentMap);
        model.addAttribute("currentTime", now);
        return "staff/verify-reservation";
    }

    @GetMapping("/staff/verify-reservation/suggest")
    @ResponseBody
    public List<java.util.Map<String, Object>> suggestReservations(@RequestParam(required = false, defaultValue = "") String q) {
        if (q == null || q.trim().isEmpty()) {
            return List.of();
        }
        // Only suggest actionable gate reservations: CONFIRMED (guaranteed arrivals), PENDING (cash-on-entry arrivals), or ACTIVE (parked vehicles)
        // Excludes CANCELLED, EXPIRED, and historical COMPLETED bookings
        List<Reservation> matches = reservationService.searchReservations(q.trim()).stream()
                .filter(r -> belongsToFacility(r, staffFacilityAccess.currentAssignedFacility().getId()))
                .filter(r -> r.getStatus() == Reservation.ReservationStatus.CONFIRMED 
                          || r.getStatus() == Reservation.ReservationStatus.PENDING 
                          || r.getStatus() == Reservation.ReservationStatus.ACTIVE
                          || r.getStatus() == Reservation.ReservationStatus.OVERSTAYING
                          || r.getStatus() == Reservation.ReservationStatus.OVERSTAY_PAYMENT_DUE)
                .toList();

        return matches.stream().limit(8).map(r -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", r.getId());
            map.put("code", r.getReservationCode() != null ? r.getReservationCode() : "RES-#" + r.getId());
            map.put("plate", plateOf(r));
            map.put("vehicle", r.getVehicle() != null ? (r.getVehicle().getBrand() + " " + r.getVehicle().getModel()) : "");
            map.put("customer", r.getCustomer() != null ? r.getCustomer().getFullName() : "Drive-Up Guest");
            map.put("bay", r.getParkingSpace() != null ? ("Bay " + r.getParkingSpace().getSpaceNumber()) : "Unassigned");
            map.put("status", r.getStatus() != null ? r.getStatus().name() : "");
            map.put("total", r.getTotalAmount() != null ? r.getTotalAmount().toString() : "0.00");
            return map;
        }).toList();
    }

    @PostMapping("/staff/verify-reservation/check-in")
    public String checkInVehicle(@RequestParam Long reservationId, RedirectAttributes redirectAttributes) {
        try {
            staffFacilityAccess.requireReservation(reservationId);
            reservationService.verifyAndCheckIn(reservationId);
            redirectAttributes.addFlashAttribute("successMessage", "Vehicle checked in successfully! Bay is now OCCUPIED.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Check-in failed: " + e.getMessage());
        }
        return "redirect:/staff/verify-reservation";
    }

    @PostMapping("/staff/verify-reservation/check-out")
    public String checkOutVehicle(@RequestParam Long reservationId, RedirectAttributes redirectAttributes) {
        try {
            staffFacilityAccess.requireReservation(reservationId);
            Reservation completed = reservationService.processCheckOutAndOverstay(reservationId);
            String msg = "Vehicle checked out successfully. Final Total: LKR " + completed.getTotalAmount();
            if (completed.getOverstayCharge().compareTo(java.math.BigDecimal.ZERO) > 0) {
                msg += " (includes LKR " + completed.getOverstayCharge() + " overstay penalty)";
            }
            redirectAttributes.addFlashAttribute("successMessage", msg);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Check-out failed: " + e.getMessage());
        }
        return "redirect:/staff/active-reservations";
    }

    @GetMapping("/staff/active-reservations")
    public String viewActiveReservations(Model model) {
        Long facilityId = staffFacilityAccess.currentAssignedFacility().getId();
        List<Reservation> activeList = reservationService.getActiveReservations().stream()
                .filter(r -> belongsToFacility(r, facilityId))
                .toList();
        java.util.Set<Long> currentlyAssignedSpaceIds = activeList.stream()
                .map(Reservation::getParkingSpace)
                .filter(java.util.Objects::nonNull)
                .map(ParkingSpace::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<ParkingSpace> availableSpaces = spaceService.getFacilityParkingSpaces(facilityId).stream()
                .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE
                        || currentlyAssignedSpaceIds.contains(s.getId()))
                .toList();
        model.addAttribute("reservations", activeList);
        model.addAttribute("availableSpaces", availableSpaces);
        return "staff/active-reservations";
    }

    private boolean belongsToFacility(Reservation reservation, Long facilityId) {
        return reservation.getParkingSpace() != null
                && reservation.getParkingSpace().getFacility() != null
                && facilityId.equals(reservation.getParkingSpace().getFacility().getId());
    }

    private String plateOf(Reservation reservation) {
        if (reservation.getVehicle() != null && reservation.getVehicle().getRegistrationNumber() != null) {
            return reservation.getVehicle().getRegistrationNumber();
        }
        return reservation instanceof WalkInReservation walkIn && walkIn.getGuestVehicleRegNumber() != null
                ? walkIn.getGuestVehicleRegNumber() : "No Plate";
    }

    @PostMapping("/staff/active-reservations/edit/{id}")
    public String editActiveReservation(@PathVariable Long id,
                                        @RequestParam String licensePlate,
                                        @RequestParam(required = false, defaultValue = "CAR") String vehicleType,
                                        @RequestParam(required = false) String brand,
                                        @RequestParam(required = false) String modelName,
                                        @RequestParam(required = false) String color,
                                        @RequestParam(required = false) String guestName,
                                        @RequestParam(required = false) String contactPhone,
                                        @RequestParam(required = false) Long parkingSpaceId,
                                        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrivalTime,
                                        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureTime,
                                        RedirectAttributes redirectAttributes) {
        try {
            staffFacilityAccess.requireReservation(id);
            if (parkingSpaceId != null) {
                staffFacilityAccess.requireSpace(parkingSpaceId);
            }
            Reservation updated = reservationService.updateActiveWalkIn(id, licensePlate, vehicleType, brand, modelName, color, guestName, contactPhone, parkingSpaceId, arrivalTime, departureTime);
            String updatedPlate = updated instanceof WalkInReservation walkIn
                    ? walkIn.getGuestVehicleRegNumber() : licensePlate;
            redirectAttributes.addFlashAttribute("successMessage", "Reservation for vehicle " + updatedPlate + " updated successfully!");
        } catch (ReservationValidationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the highlighted reservation fields.");
            redirectAttributes.addFlashAttribute("editFieldErrors", e.getFieldErrors());
            redirectAttributes.addFlashAttribute("editReservationId", id);
            redirectAttributes.addFlashAttribute("editLicensePlate", licensePlate);
            redirectAttributes.addFlashAttribute("editVehicleType", vehicleType);
            redirectAttributes.addFlashAttribute("editBrand", brand);
            redirectAttributes.addFlashAttribute("editModelName", modelName);
            redirectAttributes.addFlashAttribute("editColor", color);
            redirectAttributes.addFlashAttribute("editGuestName", guestName);
            redirectAttributes.addFlashAttribute("editContactPhone", contactPhone);
            redirectAttributes.addFlashAttribute("editDepartureTime", departureTime);
            try {
                Reservation current = reservationService.getReservationById(id);
                if (current.getParkingSpace() != null) {
                    redirectAttributes.addFlashAttribute("editParkingSpaceId", current.getParkingSpace().getId());
                }
                redirectAttributes.addFlashAttribute("editArrivalTime", current.getActualArrivalTime() != null
                        ? current.getActualArrivalTime() : current.getArrivalTime());
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "The reservation could not be updated. Refresh the queue and try again.");
        }
        return "redirect:/staff/active-reservations";
    }
}
