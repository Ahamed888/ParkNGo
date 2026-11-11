package com.example.parkngo.dashboard;

import com.example.parkngo.discount.DiscountService;
import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityService;
import com.example.parkngo.feedback.Notification;
import com.example.parkngo.feedback.NotificationService;
import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationService;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceService;
import com.example.parkngo.user.User;
import com.example.parkngo.user.UserService;
import com.example.parkngo.util.SecurityUtil;
import com.example.parkngo.staff.StaffFacilityAccessService;
import com.example.parkngo.vehicle.Vehicle;
import com.example.parkngo.vehicle.VehicleService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {
    private final UserService userService;
    private final VehicleService vehicleService;
    private final ReservationService reservationService;
    private final ParkingFacilityService facilityService;
    private final ParkingSpaceService spaceService;
    private final NotificationService notificationService;
    private final DiscountService discountService;
    private final SecurityUtil securityUtil;
    private final DashboardService dashboardService;
    private final StaffFacilityAccessService staffFacilityAccess;

    public DashboardController(UserService userService,
                               VehicleService vehicleService,
                               ReservationService reservationService,
                               ParkingFacilityService facilityService,
                               ParkingSpaceService spaceService,
                               NotificationService notificationService,
                               DiscountService discountService,
                               SecurityUtil securityUtil,
                               DashboardService dashboardService,
                               StaffFacilityAccessService staffFacilityAccess) {
        this.userService = userService;
        this.vehicleService = vehicleService;
        this.reservationService = reservationService;
        this.facilityService = facilityService;
        this.spaceService = spaceService;
        this.notificationService = notificationService;
        this.discountService = discountService;
        this.securityUtil = securityUtil;
        this.dashboardService = dashboardService;
        this.staffFacilityAccess = staffFacilityAccess;
    }

    // --- Home Page ---

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        model.addAttribute("facilities", facilityService.getAllFacilities());
        model.addAttribute("discounts", discountService.getActiveDiscounts());
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "index";
    }

    // --- Global Role-Based Dashboard Router ---

    @GetMapping("/dashboard")
    public String dashboardRouter(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().toUpperCase().contains("ADMIN"));
        if (isAdmin) {
            return "redirect:/admin/dashboard";
        }

        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_STAFF"));
        if (isStaff) {
            return "redirect:/staff/dashboard";
        }

        return "redirect:/customer/dashboard";
    }

    // --- Customer Dashboard ---

    @GetMapping("/customer/dashboard")
    public String customerDashboard(Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        List<Vehicle> vehicles = vehicleService.getCustomerVehicles(user.getId());
        List<Reservation> reservations = reservationService.getCustomerReservations(user.getId());
        List<Notification> unreadNotifications = notificationService.getUnreadNotifications(user.getId());
        List<ParkingFacility> facilities = facilityService.getAllFacilities();

        long availableSpaces = facilities.stream()
                .mapToLong(f -> spaceService.getFacilityParkingSpaces(f.getId()).stream()
                        .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE)
                        .count())
                .sum();
        if (availableSpaces == 0) {
            long totalCap = facilities.stream().mapToLong(f -> f.getTotalCapacity() != null ? f.getTotalCapacity() : 0).sum();
            long totalOcc = facilities.stream().mapToLong(f -> f.getCurrentOccupancy() != null ? f.getCurrentOccupancy() : 0).sum();
            availableSpaces = Math.max(0, totalCap - totalOcc);
        }

        model.addAttribute("user", user);
        model.addAttribute("vehicles", vehicles);
        model.addAttribute("vehicleCount", vehicles.size());
        model.addAttribute("reservations", reservations);
        model.addAttribute("unreadNotifications", unreadNotifications);
        model.addAttribute("facilities", facilities);
        model.addAttribute("availableSpaces", availableSpaces);

        return "customer/dashboard";
    }

    // --- Staff Operations Dashboard ---

    @GetMapping("/staff/dashboard")
    public String staffDashboard(@RequestParam(required = false) Long facilityId,
                                 @RequestParam(required = false) Integer floor,
                                 Model model) {
        if (securityUtil.getCurrentUser() == null) return "redirect:/login";

        ParkingFacility facility = staffFacilityAccess.requireFacility(facilityId);
        dashboardService.enrichStaffDashboard(model::addAttribute, facility.getId(), floor);
        model.addAttribute("activeReservations", reservationService.getActiveReservations().stream()
                .filter(r -> r.getParkingSpace() != null && r.getParkingSpace().getFacility() != null
                        && facility.getId().equals(r.getParkingSpace().getFacility().getId())).toList());
        return "staff/dashboard";
    }

    // --- Admin Analytics Dashboard & Reports ---

    @GetMapping("/admin/dashboard")
    public String adminDashboard(@RequestParam(required = false) LocalDate date,
                                 @RequestParam(required = false) Long facilityId,
                                 Model model) {
        if (securityUtil.getCurrentUser() == null) return "redirect:/login";

        List<User> allUsers = userService.getAllUsers();
        List<Reservation> allReservations = reservationService.getAllReservations();
        List<ParkingFacility> facilities = facilityService.getAllFacilities();

        long totalReservations = allReservations.size();
        long completedReservations = allReservations.stream()
                .filter(r -> r.getStatus() == Reservation.ReservationStatus.COMPLETED)
                .count();

        double totalRevenue = allReservations.stream()
                .filter(r -> r.getStatus() == Reservation.ReservationStatus.COMPLETED)
                .mapToDouble(r -> r.getTotalAmount().doubleValue())
                .sum();

        long totalSpaces = facilities.stream()
                .mapToLong(f -> spaceService.getFacilityParkingSpaces(f.getId()).size())
                .sum();

        long occupiedSpaces = facilities.stream()
                .mapToLong(f -> spaceService.getFacilityParkingSpaces(f.getId()).stream()
                        .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED)
                        .count())
                .sum();

        long reservedSpaces = facilities.stream()
                .mapToLong(f -> spaceService.getFacilityParkingSpaces(f.getId()).stream()
                        .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.RESERVED)
                        .count())
                .sum();

        long availableSpaces = facilities.stream()
                .mapToLong(f -> spaceService.getFacilityParkingSpaces(f.getId()).stream()
                        .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE)
                        .count())
                .sum();

        if (totalSpaces == 0) {
            totalSpaces = facilities.stream().mapToLong(f -> f.getTotalCapacity() != null ? f.getTotalCapacity() : 0).sum();
            long totalOcc = facilities.stream().mapToLong(f -> f.getCurrentOccupancy() != null ? f.getCurrentOccupancy() : 0).sum();
            availableSpaces = Math.max(0, totalSpaces - totalOcc);
            occupiedSpaces = totalOcc;
        }

        long inUseSpaces = occupiedSpaces + reservedSpaces;
        int occupancyPercentage = totalSpaces > 0 ? (int)(inUseSpaces * 100 / totalSpaces) : 0;

        model.addAttribute("totalUsers", allUsers.size());
        model.addAttribute("totalReservations", totalReservations);
        model.addAttribute("completedReservations", completedReservations);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalSpaces", totalSpaces);
        model.addAttribute("occupiedSpaces", inUseSpaces);
        model.addAttribute("reservedSpaces", reservedSpaces);
        model.addAttribute("availableSpaces", availableSpaces);
        model.addAttribute("occupancyPercentage", occupancyPercentage);

        return "admin/dashboard";
    }

    @GetMapping("/admin/reports")
    public String viewReports(Model model, @RequestParam(required = false) String type) {
        List<Reservation> reservations = reservationService.getAllReservations();

        double totalRevenue = reservations.stream()
                .mapToDouble(r -> r.getTotalAmount().doubleValue())
                .sum();

        long totalReservations = reservations.size();
        long completedReservations = reservations.stream()
                .filter(r -> r.getStatus() == Reservation.ReservationStatus.COMPLETED)
                .count();

        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalReservations", totalReservations);
        model.addAttribute("completedReservations", completedReservations);
        model.addAttribute("reportType", type);

        return "admin/reports";
    }
}
