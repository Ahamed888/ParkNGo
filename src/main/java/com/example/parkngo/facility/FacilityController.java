package com.example.parkngo.facility;

import com.example.parkngo.discount.Discount;
import com.example.parkngo.discount.DiscountService;
import com.example.parkngo.discount.Package;
import com.example.parkngo.discount.PackageService;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceRepository;
import jakarta.validation.Valid;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.parkngo.dashboard.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;

// [Presentation Layer (Spring MVC Controller)]: Intercepts HTTP requests, invokes service layer, and binds data to Thymeleaf views.
@Controller
public class FacilityController {
    private final ParkingFacilityService facilityService;
    private final FacilityServiceService facilityServiceService;
    private final PackageService packageService;
    private final DiscountService discountService;
    private final ParkingSpaceRepository parkingSpaceRepository;
    private final DashboardService dashboardService;

    @Autowired(required = false)
    private com.example.parkngo.user.AdminRepository adminRepository;

    public FacilityController(ParkingFacilityService facilityService,
                              FacilityServiceService facilityServiceService,
                              PackageService packageService,
                              DiscountService discountService,
                              ParkingSpaceRepository parkingSpaceRepository,
                              @Autowired(required = false) DashboardService dashboardService) {
        this.facilityService = facilityService;
        this.facilityServiceService = facilityServiceService;
        this.packageService = packageService;
        this.discountService = discountService;
        this.parkingSpaceRepository = parkingSpaceRepository;
        this.dashboardService = dashboardService;
    }

    // ==========================================
    // --- Admin Facility Management Routes ---
    // ==========================================

    // [@GetMapping]: Handles HTTP GET to retrieve and render views idempotently without mutating server state.
    @GetMapping("/admin/facilities")
    public String viewFacilities(@RequestParam(required = false) String keyword, Model model) {
        List<ParkingFacility> facilities;
        try {
            facilities = (keyword != null && !keyword.trim().isEmpty())
                    ? facilityService.searchFacilities(keyword.trim(), true)
                    : facilityService.getAllFacilitiesIncludingInactive();
        } catch (Exception e) {
            facilities = List.of();
        }

        int totalBays = facilities.stream()
                .mapToInt(f -> f.getTotalCapacity() != null ? f.getTotalCapacity() : 0)
                .sum();
        int totalOccupancy = facilities.stream()
                .mapToInt(f -> f.getCurrentOccupancy() != null ? f.getCurrentOccupancy() : 0)
                .sum();
        int avgOccupancyPercent = totalBays > 0 ? (int) Math.round(((double) totalOccupancy / totalBays) * 100) : 0;

        // Map facility ID to services list and spaces count
        Map<Long, List<FacilityService>> servicesMap = new HashMap<>();
        Map<Long, Long> spacesCountMap = new HashMap<>();
        for (ParkingFacility facility : facilities) {
            try {
                servicesMap.put(facility.getId(), facilityServiceService.getFacilityServices(facility.getId()));
            } catch (Exception e) {
                servicesMap.put(facility.getId(), List.of());
            }
            try {
                spacesCountMap.put(facility.getId(), parkingSpaceRepository.countByFacilityId(facility.getId()));
            } catch (Exception e) {
                spacesCountMap.put(facility.getId(), 0L);
            }
        }

        model.addAttribute("facilities", facilities);
        model.addAttribute("servicesMap", servicesMap);
        model.addAttribute("spacesCountMap", spacesCountMap);
        model.addAttribute("totalFacilities", facilities.size());
        model.addAttribute("activeFacilities", facilities.stream().filter(ParkingFacility::isActive).count());
        model.addAttribute("totalBays", totalBays);
        model.addAttribute("totalOccupancy", totalOccupancy);
        model.addAttribute("avgOccupancyPercent", avgOccupancyPercent);
        model.addAttribute("keyword", keyword != null ? keyword.trim() : "");

        return "admin/facilities";
    }

    @GetMapping({"/admin/facilities/{id:[0-9]+}", "/admin/facility/{id:[0-9]+}"})
    public String viewFacilityDetails(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            ParkingFacility facility = facilityService.getFacilityById(id);
            List<FacilityService> services = facilityServiceService.getFacilityServices(id);
            List<ParkingSpace> spaces = parkingSpaceRepository.findByFacilityId(id);

            long totalSpaces = spaces.size();
            long availableSpaces = spaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE).count();
            long occupiedSpaces = spaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED).count();
            long reservedSpaces = spaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.RESERVED).count();
            long maintenanceSpaces = spaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.MAINTENANCE).count();
            long evSpaces = spaces.stream().filter(s -> Boolean.TRUE.equals(s.getHasEVCharging())).count();
            long accessibleSpaces = spaces.stream().filter(s -> Boolean.TRUE.equals(s.getIsAccessible())).count();

            model.addAttribute("facility", facility);
            model.addAttribute("services", services);
            model.addAttribute("spaces", spaces);
            model.addAttribute("totalSpaces", totalSpaces);
            model.addAttribute("availableSpaces", availableSpaces);
            model.addAttribute("occupiedSpaces", occupiedSpaces);
            model.addAttribute("reservedSpaces", reservedSpaces);
            model.addAttribute("maintenanceSpaces", maintenanceSpaces);
            model.addAttribute("evSpaces", evSpaces);
            model.addAttribute("accessibleSpaces", accessibleSpaces);
            model.addAttribute("staffLoginEmail",
                    com.example.parkngo.user.FacilityStaffAccountService.emailFor(facility.getId()));
            model.addAttribute("staffLoginPassword",
                    com.example.parkngo.user.FacilityStaffAccountService.passwordFor(facility.getId()));

            if (dashboardService != null) {
                dashboardService.enrichFacilityDetails(model::addAttribute, facility, spaces, services);
            } else {
                model.addAttribute("floorSummaries", List.of());
                model.addAttribute("recentActivity", List.of());
            }

            return "admin/facility-details";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Parking complex not found: " + e.getMessage());
            return "redirect:/admin/facilities";
        }
    }

    @GetMapping({"/admin/facilities/create", "/admin/facility/add", "/admin/facilities/add", "/admin/facility/create"})
    public String addFacilityForm(Model model) {
        ParkingFacility facility = new ParkingFacility();
        facility.setOpeningTime(LocalTime.of(6, 0));
        facility.setClosingTime(LocalTime.of(23, 0));
        facility.setTotalCapacity(100);
        facility.setTotalFloors(1);
        facility.setCurrentOccupancy(0);
        facility.setStatus(ParkingFacility.FacilityStatus.ACTIVE);
        facility.setCity("Colombo");
        facility.setPostalCode("00100");
        model.addAttribute("facility", facility);
        model.addAttribute("isEdit", false);
        return "admin/facility-form";
    }

    // [ @PostMapping & Validation]: Handles form submission; @Valid triggers Bean Validation and BindingResult captures errors.
    // [Post/Redirect/Get (PRG) Pattern]: Redirects on success to prevent duplicate form submissions upon browser refresh.
    @PostMapping({"/admin/facilities/create", "/admin/facility/add", "/admin/facilities/add", "/admin/facility/create"})
    public String addFacility(@Valid @ModelAttribute("facility") ParkingFacility facility,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model,
                              org.springframework.security.core.Authentication authentication) {
        if (bindingResult.hasErrors()) {
            List<String> validationErrors = bindingResult.getAllErrors().stream()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .filter(Objects::nonNull)
                    .toList();
            model.addAttribute("facility", facility);
            model.addAttribute("isEdit", false);
            model.addAttribute("validationErrors", validationErrors);
            model.addAttribute("error", "Please fix the validation errors below.");
            return "admin/facility-form";
        }
        try {
            if (authentication != null && adminRepository != null && (facility.getManagedByAdminId() == null || facility.getManagedByAdminId() <= 0)) {
                adminRepository.findByEmail(authentication.getName()).ifPresent(a -> facility.setManagedByAdminId(a.getId()));
            }
            ParkingFacility savedFacility = facilityService.createFacility(facility);
            redirectAttributes.addFlashAttribute("success", "Parking complex '" + savedFacility.getName()
                    + "' and its assigned staff account were created successfully.");
            redirectAttributes.addFlashAttribute("createdFacilityName", savedFacility.getName());
            redirectAttributes.addFlashAttribute("staffLoginEmail",
                    com.example.parkngo.user.FacilityStaffAccountService.emailFor(savedFacility.getId()));
            redirectAttributes.addFlashAttribute("staffLoginPassword",
                    com.example.parkngo.user.FacilityStaffAccountService.passwordFor(savedFacility.getId()));
            return "redirect:/admin/facilities";
        } catch (IllegalArgumentException e) {
            model.addAttribute("facility", facility);
            model.addAttribute("isEdit", false);
            model.addAttribute("error", e.getMessage());
            return "admin/facility-form";
        } catch (Exception e) {
            model.addAttribute("facility", facility);
            model.addAttribute("isEdit", false);
            model.addAttribute("error", "Failed to add parking complex: " + e.getMessage());
            return "admin/facility-form";
        }
    }

    @GetMapping({"/admin/facilities/edit/{id}", "/admin/facility/edit/{id}"})
    public String editFacilityForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            ParkingFacility facility = facilityService.getFacilityById(id);
            model.addAttribute("facility", facility);
            model.addAttribute("isEdit", true);
            return "admin/facility-form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Parking complex not found: " + e.getMessage());
            return "redirect:/admin/facilities";
        }
    }

    @PostMapping({"/admin/facilities/edit/{id}", "/admin/facility/edit/{id}", "/admin/facilities/update/{id}", "/admin/facility/update/{id}"})
    public String updateFacility(@PathVariable Long id,
                                 @Valid @ModelAttribute("facility") ParkingFacility facility,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            List<String> validationErrors = bindingResult.getAllErrors().stream()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .filter(Objects::nonNull)
                    .toList();
            model.addAttribute("facility", facility);
            model.addAttribute("isEdit", true);
            model.addAttribute("validationErrors", validationErrors);
            model.addAttribute("error", "Please fix the validation errors below.");
            return "admin/facility-form";
        }
        try {
            facility.setId(id);
            facilityService.updateFacility(id, facility);
            redirectAttributes.addFlashAttribute("success", "Parking complex '" + facility.getName() + "' updated successfully!");
            return "redirect:/admin/facilities";
        } catch (IllegalStateException | IllegalArgumentException e) {
            model.addAttribute("facility", facility);
            model.addAttribute("isEdit", true);
            model.addAttribute("error", e.getMessage());
            return "admin/facility-form";
        } catch (Exception e) {
            model.addAttribute("facility", facility);
            model.addAttribute("isEdit", true);
            model.addAttribute("error", "Failed to update parking complex: " + e.getMessage());
            return "admin/facility-form";
        }
    }

    // [ Status Toggle Endpoint]: Safely toggles facility ACTIVE/INACTIVE state via service layer without deleting records.
    @PostMapping({"/admin/facilities/{id}/toggle-status", "/admin/facility/{id}/toggle-status"})
    public String toggleFacilityStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ParkingFacility updated = facilityService.toggleFacilityStatus(id);
            String action = updated.isActive() ? "reactivated" : "deactivated";
            redirectAttributes.addFlashAttribute("success", "Parking complex '" + updated.getName() + "' has been " + action + ".");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update parking complex status: " + e.getMessage());
        }
        return "redirect:/admin/facilities";
    }

    @PostMapping({"/admin/facilities/delete/{id}", "/admin/facility/delete/{id}"})
    public String deleteFacility(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            facilityService.deactivateFacility(id);
            redirectAttributes.addFlashAttribute("success", "Parking complex deactivated successfully.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to deactivate parking complex: " + e.getMessage());
        }
        return "redirect:/admin/facilities";
    }

    @PostMapping({"/admin/facilities/{id}/hard-delete", "/admin/facility/{id}/hard-delete"})
    public String hardDeleteFacility(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            facilityService.hardDeleteFacility(id);
            redirectAttributes.addFlashAttribute("success", "Parking complex permanently deleted.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to permanently delete parking complex: " + e.getMessage());
        }
        return "redirect:/admin/facilities";
    }

    // =======================================================
    // --- Admin Facility Services / Amenities Management ---
    // =======================================================

    @GetMapping("/admin/services")
    public String viewServices(@RequestParam(required = false) Long facilityId, Model model) {
        List<FacilityService> services;
        try {
            if (facilityId != null && facilityId > 0) {
                services = facilityServiceService.getFacilityServices(facilityId);
            } else {
                services = facilityServiceService.getAllServices();
            }
        } catch (Exception e) {
            services = List.of();
        }

        List<ParkingFacility> facilities;
        try {
            facilities = facilityService.getAllFacilitiesIncludingInactive();
        } catch (Exception e) {
            facilities = List.of();
        }

        long availableCount = services.stream().filter(FacilityService::isAvailable).count();
        BigDecimal avgPrice = BigDecimal.ZERO;
        if (!services.isEmpty()) {
            BigDecimal totalPrice = services.stream()
                    .map(FacilityService::getPrice)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgPrice = totalPrice.divide(BigDecimal.valueOf(services.size()), 2, RoundingMode.HALF_UP);
        }

        model.addAttribute("services", services);
        model.addAttribute("facilities", facilities);
        model.addAttribute("selectedFacilityId", facilityId);
        model.addAttribute("totalServices", services.size());
        model.addAttribute("availableCount", availableCount);
        model.addAttribute("avgPrice", avgPrice);

        return "admin/services";
    }

    @GetMapping({"/admin/services/create", "/admin/service/add", "/admin/services/add", "/admin/service/create"})
    public String addServiceForm(@RequestParam(required = false) Long facilityId, Model model) {
        List<ParkingFacility> facilities = facilityService.getAllFacilities();
        FacilityService service = new FacilityService();
        service.setStatus(FacilityService.ServiceStatus.AVAILABLE);

        model.addAttribute("service", service);
        model.addAttribute("facilities", facilities);
        model.addAttribute("selectedFacilityIds", facilityId == null ? List.of() : List.of(facilityId));
        model.addAttribute("inactiveAssignedFacilities", List.of());
        model.addAttribute("isEdit", false);
        return "admin/service-form";
    }

    @PostMapping({"/admin/services/create", "/admin/service/add", "/admin/services/add", "/admin/service/create"})
    public String addService(@Valid @ModelAttribute("service") FacilityService service,
                             BindingResult bindingResult,
                             @RequestParam(name = "facilityIds", required = false) List<Long> facilityIds,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        List<ParkingFacility> facilities = facilityService.getAllFacilities();
        if (bindingResult.hasErrors() || facilityIds == null || facilityIds.isEmpty()) {
            model.addAttribute("service", service);
            model.addAttribute("facilities", facilities);
            model.addAttribute("selectedFacilityIds", facilityIds == null ? List.of() : facilityIds);
            model.addAttribute("inactiveAssignedFacilities", List.of());
            model.addAttribute("isEdit", false);
            if (facilityIds == null || facilityIds.isEmpty()) {
                model.addAttribute("facilityError", "Please select at least one active parking complex.");
                model.addAttribute("error", "Please select at least one active parking complex.");
            }
            return "admin/service-form";
        }
        try {
            facilityServiceService.createService(service, facilityIds);
            redirectAttributes.addFlashAttribute("success", "Service '" + service.getName() + "' added successfully!");
            return "redirect:/admin/services";
        } catch (IllegalArgumentException e) {
            model.addAttribute("service", service);
            model.addAttribute("facilities", facilities);
            model.addAttribute("selectedFacilityIds", facilityIds);
            model.addAttribute("inactiveAssignedFacilities", List.of());
            model.addAttribute("isEdit", false);
            model.addAttribute("error", e.getMessage());
            return "admin/service-form";
        } catch (Exception e) {
            model.addAttribute("service", service);
            model.addAttribute("facilities", facilities);
            model.addAttribute("selectedFacilityIds", facilityIds);
            model.addAttribute("inactiveAssignedFacilities", List.of());
            model.addAttribute("isEdit", false);
            model.addAttribute("error", "Failed to add service: " + e.getMessage());
            return "admin/service-form";
        }
    }

    @GetMapping({"/admin/services/edit/{id}", "/admin/service/edit/{id}"})
    public String editServiceForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            FacilityService service = facilityServiceService.getServiceById(id);
            List<ParkingFacility> facilities = facilityService.getAllFacilities();
            List<Long> selectedFacilityIds = service.getFacilities().stream()
                    .filter(ParkingFacility::isActive)
                    .map(ParkingFacility::getId)
                    .toList();
            List<ParkingFacility> inactiveAssignedFacilities = service.getFacilities().stream()
                    .filter(facility -> !facility.isActive())
                    .toList();

            model.addAttribute("service", service);
            model.addAttribute("facilities", facilities);
            model.addAttribute("selectedFacilityIds", selectedFacilityIds);
            model.addAttribute("inactiveAssignedFacilities", inactiveAssignedFacilities);
            model.addAttribute("isEdit", true);
            return "admin/service-form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Service not found: " + e.getMessage());
            return "redirect:/admin/services";
        }
    }

    @PostMapping({"/admin/services/edit/{id}", "/admin/service/edit/{id}", "/admin/services/update/{id}", "/admin/service/update/{id}"})
    public String updateService(@PathVariable Long id,
                                @Valid @ModelAttribute("service") FacilityService service,
                                BindingResult bindingResult,
                                @RequestParam(name = "facilityIds", required = false) List<Long> facilityIds,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        List<ParkingFacility> facilities = facilityService.getAllFacilities();
        List<ParkingFacility> inactiveAssignedFacilities = getInactiveAssignments(id);
        boolean hasPreservedInactiveAssignment = !inactiveAssignedFacilities.isEmpty();
        if (bindingResult.hasErrors() || ((facilityIds == null || facilityIds.isEmpty()) && !hasPreservedInactiveAssignment)) {
            model.addAttribute("service", service);
            model.addAttribute("facilities", facilities);
            model.addAttribute("selectedFacilityIds", facilityIds == null ? List.of() : facilityIds);
            model.addAttribute("inactiveAssignedFacilities", inactiveAssignedFacilities);
            model.addAttribute("isEdit", true);
            if ((facilityIds == null || facilityIds.isEmpty()) && !hasPreservedInactiveAssignment) {
                model.addAttribute("facilityError", "Please select at least one active parking complex.");
                model.addAttribute("error", "Please select at least one active parking complex.");
            }
            return "admin/service-form";
        }
        try {
            service.setId(id);
            facilityServiceService.updateService(id, service, facilityIds == null ? List.of() : facilityIds);
            redirectAttributes.addFlashAttribute("success", "Service '" + service.getName() + "' updated successfully!");
            return "redirect:/admin/services";
        } catch (IllegalArgumentException e) {
            model.addAttribute("service", service);
            model.addAttribute("facilities", facilities);
            model.addAttribute("selectedFacilityIds", facilityIds == null ? List.of() : facilityIds);
            model.addAttribute("inactiveAssignedFacilities", inactiveAssignedFacilities);
            model.addAttribute("isEdit", true);
            model.addAttribute("error", e.getMessage());
            return "admin/service-form";
        } catch (Exception e) {
            model.addAttribute("service", service);
            model.addAttribute("facilities", facilities);
            model.addAttribute("selectedFacilityIds", facilityIds == null ? List.of() : facilityIds);
            model.addAttribute("inactiveAssignedFacilities", inactiveAssignedFacilities);
            model.addAttribute("isEdit", true);
            model.addAttribute("error", "Failed to update service: " + e.getMessage());
            return "admin/service-form";
        }
    }

    private List<ParkingFacility> getInactiveAssignments(Long serviceId) {
        try {
            return facilityServiceService.getServiceById(serviceId).getFacilities().stream()
                    .filter(facility -> !facility.isActive())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @PostMapping({"/admin/services/{id}/hard-delete", "/admin/service/{id}/hard-delete", "/admin/services/delete/{id}", "/admin/service/delete/{id}"})
    public String hardDeleteService(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            facilityServiceService.hardDeleteService(id);
            redirectAttributes.addFlashAttribute("success", "Service deleted successfully.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete service: " + e.getMessage());
        }
        return "redirect:/admin/services";
    }

    // ==============================================
    // --- Customer Parking & Facility Directory ---
    // ==============================================

    @GetMapping("/customer/parking-search")
    public String searchParking(@RequestParam(required = false) String keyword, Model model) {
        List<ParkingFacility> allFacilities = facilityService.getAllFacilities();
        List<ParkingFacility> facilities = (keyword != null && !keyword.trim().isEmpty())
                ? facilityService.searchFacilities(keyword.trim())
                : allFacilities;

        model.addAttribute("facilities", facilities);
        model.addAttribute("allFacilities", allFacilities);
        model.addAttribute("keyword", keyword != null ? keyword.trim() : "");

        return "customer/parking-search";
    }

    @GetMapping("/customer/facilities/suggest")
    @ResponseBody
    public List<java.util.Map<String, String>> suggestFacilities(@RequestParam(required = false, defaultValue = "") String q) {
        String query = (q != null) ? q.trim().toLowerCase() : "";
        List<ParkingFacility> all = facilityService.getAllFacilities();
        return all.stream()
                .filter(f -> f.getStatus() == ParkingFacility.FacilityStatus.ACTIVE)
                .filter(f -> query.isEmpty()
                        || (f.getName() != null && f.getName().toLowerCase().contains(query))
                        || (f.getCity() != null && f.getCity().toLowerCase().contains(query))
                        || (f.getAddress() != null && f.getAddress().toLowerCase().contains(query)))
                .map(f -> {
                    java.util.Map<String, String> item = new java.util.HashMap<>();
                    item.put("name", f.getName());
                    item.put("city", f.getCity() != null ? f.getCity() : "");
                    item.put("address", f.getAddress() != null ? f.getAddress() : "");
                    return item;
                })
                .limit(10)
                .toList();
    }
}
