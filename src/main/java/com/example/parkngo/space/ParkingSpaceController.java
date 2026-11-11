package com.example.parkngo.space;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityService;
import com.example.parkngo.staff.StaffFacilityAccessService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class ParkingSpaceController {
    private final ParkingSpaceService spaceService;
    private final ParkingFacilityService facilityService;
    private final StaffFacilityAccessService staffFacilityAccess;

    public ParkingSpaceController(ParkingSpaceService spaceService, ParkingFacilityService facilityService,
                                  StaffFacilityAccessService staffFacilityAccess) {
        this.spaceService = spaceService;
        this.facilityService = facilityService;
        this.staffFacilityAccess = staffFacilityAccess;
    }

    // --- Admin Parking Space Management ---

    @GetMapping({"/admin/parking-spaces", "/admin/spaces"})
    public String viewParkingSpacesAdmin(
            Model model,
            @RequestParam(required = false) Long facilityId,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String allocationType,
            @RequestParam(required = false) String search) {

        ParkingSpace.ParkingSpaceStatus parsedStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                parsedStatus = ParkingSpace.ParkingSpaceStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        List<ParkingFacility> facilities = facilityService.getAllFacilities();
        List<ParkingSpace> spaces = spaceService.filterSpaces(facilityId, floor, zone, parsedStatus, allocationType, search);

        // Overall stats
        List<ParkingSpace> allSpaces = spaceService.getAllParkingSpaces();
        long totalCount = allSpaces.size();
        long availableCount = allSpaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE).count();
        long occupiedCount = allSpaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED).count();
        long maintenanceCount = allSpaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.MAINTENANCE).count();
        long evCount = allSpaces.stream().filter(s -> Boolean.TRUE.equals(s.getHasEVCharging())).count();
        long accessibleCount = allSpaces.stream().filter(s -> Boolean.TRUE.equals(s.getIsAccessible())).count();

        List<java.util.Map<String, Object>> storedSpaces = allSpaces.stream()
                .filter(s -> s.getSpaceNumber() != null && !s.getSpaceNumber().isBlank())
                .map(s -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("spaceNumber", s.getSpaceNumber());
                    map.put("facilityId", s.getFacility() != null ? s.getFacility().getId() : null);
                    map.put("facilityName", s.getFacility() != null ? s.getFacility().getName() : "Unassigned");
                    map.put("floor", s.getFloor() != null ? s.getFloor() : 1);
                    map.put("zone", s.getZone() != null ? s.getZone() : "");
                    map.put("status", s.getStatus() != null ? s.getStatus().name() : "AVAILABLE");
                    map.put("parkingType", s.getParkingType() != null ? s.getParkingType() : "STANDARD");
                    return map;
                })
                .sorted((a, b) -> ((String) a.get("spaceNumber")).compareToIgnoreCase((String) b.get("spaceNumber")))
                .toList();

        model.addAttribute("facilities", facilities);
        model.addAttribute("spaces", spaces);
        model.addAttribute("storedSpaces", storedSpaces);
        model.addAttribute("selectedFacilityId", facilityId);
        model.addAttribute("selectedFloor", floor);
        model.addAttribute("selectedZone", zone);
        model.addAttribute("selectedStatus", parsedStatus);
        model.addAttribute("selectedAllocationType", allocationType);
        model.addAttribute("searchQuery", search);

        model.addAttribute("totalCount", totalCount);
        model.addAttribute("availableCount", availableCount);
        model.addAttribute("occupiedCount", occupiedCount);
        model.addAttribute("maintenanceCount", maintenanceCount);
        model.addAttribute("evCount", evCount);
        model.addAttribute("accessibleCount", accessibleCount);

        return "admin/parking-spaces";
    }

    @GetMapping({"/admin/parking-spaces/suggest", "/admin/spaces/suggest"})
    @ResponseBody
    public List<java.util.Map<String, Object>> suggestSpaceCodes(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) Long facilityId) {
        if (q == null || q.trim().isEmpty()) {
            return List.of();
        }
        String query = q.trim().toLowerCase();
        List<ParkingSpace> spaces = (facilityId != null)
                ? spaceService.getFacilityParkingSpaces(facilityId)
                : spaceService.getAllParkingSpaces();

        return spaces.stream()
                .filter(s -> s.getSpaceNumber() != null && s.getSpaceNumber().toLowerCase().contains(query))
                .sorted((a, b) -> {
                    boolean aStarts = a.getSpaceNumber().toLowerCase().startsWith(query);
                    boolean bStarts = b.getSpaceNumber().toLowerCase().startsWith(query);
                    if (aStarts != bStarts) {
                        return aStarts ? -1 : 1;
                    }
                    return a.getSpaceNumber().compareToIgnoreCase(b.getSpaceNumber());
                })
                .limit(10)
                .map(s -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("spaceNumber", s.getSpaceNumber());
                    map.put("facilityId", s.getFacility() != null ? s.getFacility().getId() : null);
                    map.put("facilityName", s.getFacility() != null ? s.getFacility().getName() : "Unassigned");
                    map.put("floor", s.getFloor() != null ? s.getFloor() : 1);
                    map.put("zone", s.getZone() != null ? s.getZone() : "");
                    map.put("status", s.getStatus() != null ? s.getStatus().name() : "AVAILABLE");
                    map.put("parkingType", s.getParkingType() != null ? s.getParkingType() : "STANDARD");
                    return map;
                })
                .toList();
    }

    @GetMapping({"/admin/parking-space/add", "/admin/parking-spaces/add", "/admin/parking-spaces/create", "/admin/spaces/create", "/admin/space/add"})
    public String addSpaceForm(Model model) {
        List<ParkingFacility> facilities = facilityService.getAllFacilities();
        ParkingSpace space = new ParkingSpace();
        space.setFloor(1);
        space.setZone("ZONE-A");
        space.setParkingType("STANDARD");
        space.setHourlyRate(java.math.BigDecimal.valueOf(250));
        space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);

        model.addAttribute("space", space);
        model.addAttribute("facilities", facilities);
        model.addAttribute("isEdit", false);
        return "admin/space-form";
    }

    @GetMapping("/admin/parking-space/generate-code")
    @ResponseBody
    public java.util.Map<String, String> generateSpaceCode(
            @RequestParam(required = false) Long facilityId,
            @RequestParam(required = false, defaultValue = "1") Integer floor,
            @RequestParam(required = false, defaultValue = "ZONE-A") String zone) {
        if (facilityId == null) {
            return java.util.Map.of("spaceNumber", "");
        }
        String code = spaceService.generateNextSpaceNumber(facilityId, floor, zone);
        return java.util.Map.of("spaceNumber", code);
    }

    @PostMapping("/admin/parking-space/add")
    public String addSpace(@Valid @ModelAttribute("space") ParkingSpace space,
                           BindingResult bindingResult,
                           @RequestParam(required = false) Long facilityId,
                           org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes,
                           Model model) {
        List<ParkingFacility> facilities = facilityService.getAllFacilities();
        if (bindingResult.hasErrors()) {
            model.addAttribute("facilities", facilities);
            model.addAttribute("isEdit", false);
            model.addAttribute("error", "Please correct the highlighted parking-space fields.");
            return "admin/space-form";
        }
        if (facilityId == null) {
            model.addAttribute("facilityError", "Please select a parking complex.");
            model.addAttribute("error", "Please select a parking complex.");
            model.addAttribute("facilities", facilities);
            model.addAttribute("space", space);
            model.addAttribute("isEdit", false);
            return "admin/space-form";
        }
        if (space.getSpaceNumber() == null || space.getSpaceNumber().trim().isEmpty()) {
            model.addAttribute("error", "Space code / number is required (e.g. L1S01, A01).");
            model.addAttribute("facilities", facilities);
            model.addAttribute("space", space);
            model.addAttribute("isEdit", false);
            return "admin/space-form";
        }
        if (space.getFloor() == null || space.getFloor() < 1) {
            model.addAttribute("error", "Floor level must be at least 1.");
            model.addAttribute("facilities", facilities);
            model.addAttribute("space", space);
            model.addAttribute("isEdit", false);
            return "admin/space-form";
        }
        if (space.getHourlyRate() == null || space.getHourlyRate().compareTo(java.math.BigDecimal.ZERO) < 0) {
            model.addAttribute("error", "Hourly rate must be a valid positive number in LKR.");
            model.addAttribute("facilities", facilities);
            model.addAttribute("space", space);
            model.addAttribute("isEdit", false);
            return "admin/space-form";
        }
        try {
            ParkingFacility facility = facilityService.getFacilityById(facilityId);
            int maxFloor = facility.getTotalFloors() != null && facility.getTotalFloors() >= 1 ? facility.getTotalFloors() : 1;
            if (space.getFloor() == null || space.getFloor() < 1 || space.getFloor() > maxFloor) {
                bindingResult.rejectValue("floor", "range", "Floor must be between 1 and " + maxFloor + " for this parking complex.");
                model.addAttribute("error", "Please correct the highlighted parking-space fields.");
                model.addAttribute("facilities", facilities);
                model.addAttribute("space", space);
                model.addAttribute("isEdit", false);
                return "admin/space-form";
            }
            space.setFacility(facility);
            if (space.getHasEVCharging() == null) {
                space.setHasEVCharging("ELECTRIC".equalsIgnoreCase(space.getParkingType()) || "EV_CHARGING".equalsIgnoreCase(space.getParkingType()));
            }
            if (space.getIsAccessible() == null) {
                space.setIsAccessible("HANDICAPPED".equalsIgnoreCase(space.getParkingType()) || "ACCESSIBLE".equalsIgnoreCase(space.getParkingType()));
            }
            if (space.getStatus() == null) {
                space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
            }
            spaceService.createParkingSpace(space);
            redirectAttributes.addFlashAttribute("success", "Parking space '" + space.getSpaceNumber() + "' created successfully!");
            return "redirect:/admin/parking-spaces";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to add parking space: " + e.getMessage());
            model.addAttribute("facilities", facilities);
            model.addAttribute("space", space);
            model.addAttribute("isEdit", false);
            return "admin/space-form";
        }
    }

    @GetMapping("/admin/parking-space/edit/{id}")
    public String editSpaceForm(@PathVariable Long id,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes,
                                Model model) {
        try {
            ParkingSpace space = spaceService.getParkingSpaceById(id);
            List<ParkingFacility> facilities = facilityService.getAllFacilities();
            model.addAttribute("space", space);
            model.addAttribute("facilities", facilities);
            model.addAttribute("isEdit", true);
            return "admin/space-form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Parking space not found: " + e.getMessage());
            return "redirect:/admin/parking-spaces";
        }
    }

    @PostMapping("/admin/parking-space/edit/{id}")
    public String editSpace(@PathVariable Long id,
                            @Valid @ModelAttribute("space") ParkingSpace space,
                            BindingResult bindingResult,
                            @RequestParam(required = false) Long facilityId,
                            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes,
                            Model model) {
        List<ParkingFacility> facilities = facilityService.getAllFacilities();
        if (bindingResult.hasErrors()) {
            model.addAttribute("facilities", facilities);
            model.addAttribute("isEdit", true);
            model.addAttribute("error", "Please correct the highlighted parking-space fields.");
            return "admin/space-form";
        }
        if (facilityId == null) {
            model.addAttribute("facilityError", "Please select a parking complex.");
            model.addAttribute("error", "Please select a parking complex.");
            model.addAttribute("facilities", facilities);
            model.addAttribute("space", space);
            model.addAttribute("isEdit", true);
            return "admin/space-form";
        }
        if (space.getSpaceNumber() == null || space.getSpaceNumber().trim().isEmpty()) {
            model.addAttribute("error", "Space code / number is required (e.g. L1S01, A01).");
            model.addAttribute("facilities", facilities);
            model.addAttribute("space", space);
            model.addAttribute("isEdit", true);
            return "admin/space-form";
        }
        if (space.getFloor() == null || space.getFloor() < 1) {
            model.addAttribute("error", "Floor level must be at least 1.");
            model.addAttribute("facilities", facilities);
            model.addAttribute("space", space);
            model.addAttribute("isEdit", true);
            return "admin/space-form";
        }
        if (space.getHourlyRate() == null || space.getHourlyRate().compareTo(java.math.BigDecimal.ZERO) < 0) {
            model.addAttribute("error", "Hourly rate must be a valid positive number in LKR.");
            model.addAttribute("facilities", facilities);
            model.addAttribute("space", space);
            model.addAttribute("isEdit", true);
            return "admin/space-form";
        }
        try {
            ParkingFacility facility = facilityService.getFacilityById(facilityId);
            int maxFloor = facility.getTotalFloors() != null && facility.getTotalFloors() >= 1 ? facility.getTotalFloors() : 1;
            if (space.getFloor() == null || space.getFloor() < 1 || space.getFloor() > maxFloor) {
                bindingResult.rejectValue("floor", "range", "Floor must be between 1 and " + maxFloor + " for this parking complex.");
                model.addAttribute("error", "Please correct the highlighted parking-space fields.");
                model.addAttribute("facilities", facilities);
                model.addAttribute("space", space);
                model.addAttribute("isEdit", true);
                return "admin/space-form";
            }
            space.setFacility(facility);
            if (space.getHasEVCharging() == null) {
                space.setHasEVCharging("ELECTRIC".equalsIgnoreCase(space.getParkingType()) || "EV_CHARGING".equalsIgnoreCase(space.getParkingType()));
            }
            if (space.getIsAccessible() == null) {
                space.setIsAccessible("HANDICAPPED".equalsIgnoreCase(space.getParkingType()) || "ACCESSIBLE".equalsIgnoreCase(space.getParkingType()));
            }
            spaceService.updateParkingSpace(id, space);
            redirectAttributes.addFlashAttribute("success", "Parking space '" + space.getSpaceNumber() + "' updated successfully!");
            return "redirect:/admin/parking-spaces";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to update parking space: " + e.getMessage());
            model.addAttribute("facilities", facilities);
            model.addAttribute("space", space);
            model.addAttribute("isEdit", true);
            return "admin/space-form";
        }
    }

    @PostMapping("/admin/parking-space/delete/{id}")
    public String deleteSpace(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            spaceService.deleteParkingSpace(id);
            redirectAttributes.addFlashAttribute("success", "Parking space deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete parking space: " + e.getMessage());
        }
        return "redirect:/admin/parking-spaces";
    }

    // --- Staff Parking Space & Occupancy Views ---

    @GetMapping("/staff/parking-spaces")
    public String viewParkingSpacesStaff(
            Model model,
            @RequestParam(required = false) Long facilityId,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String status) {
        try {
            ParkingFacility facility = staffFacilityAccess.requireFacility(facilityId);

            ParkingSpace.ParkingSpaceStatus parsedStatus = null;
            if (status != null && !status.trim().isEmpty()) {
                try {
                    parsedStatus = ParkingSpace.ParkingSpaceStatus.valueOf(status.trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }

            Long targetFacilityId = facility.getId();
            List<ParkingSpace> spaces = spaceService.filterSpaces(targetFacilityId, floor, zone, parsedStatus, null, null);

            List<ParkingSpace> allFacilitySpaces = spaceService.getFacilityParkingSpaces(targetFacilityId);
            long total = allFacilitySpaces.size();
            long available = allFacilitySpaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE).count();
            long occupied = allFacilitySpaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED).count();
            long reserved = allFacilitySpaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.RESERVED).count();
            long maintenance = allFacilitySpaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.MAINTENANCE).count();

            model.addAttribute("facility", facility);
            model.addAttribute("spaces", spaces);
            model.addAttribute("facilities", List.of(facility));
            model.addAttribute("selectedFloor", floor);
            model.addAttribute("selectedZone", zone);
            model.addAttribute("selectedStatus", status);

            model.addAttribute("totalSpaces", total);
            model.addAttribute("availableSpaces", available);
            model.addAttribute("occupiedSpaces", occupied);
            model.addAttribute("reservedSpaces", reserved);
            model.addAttribute("maintenanceSpaces", maintenance);

            return "staff/parking-spaces";
        } catch (Exception e) {
            model.addAttribute("error", "Parking complex not found: " + e.getMessage());
            return "staff/parking-spaces";
        }
    }

    @PostMapping("/staff/parking-space/{id}/status")
    public String updateSpaceStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) Long facilityId,
            RedirectAttributes redirectAttributes) {
        try {
            staffFacilityAccess.requireSpace(id);
            ParkingSpace.ParkingSpaceStatus newStatus = ParkingSpace.ParkingSpaceStatus.valueOf(status.trim().toUpperCase());
            if (newStatus != ParkingSpace.ParkingSpaceStatus.MAINTENANCE
                    && newStatus != ParkingSpace.ParkingSpaceStatus.OUT_OF_SERVICE
                    && newStatus != ParkingSpace.ParkingSpaceStatus.AVAILABLE) {
                throw new IllegalArgumentException("Staff may only set a space to MAINTENANCE, OUT_OF_SERVICE, or restore it to AVAILABLE.");
            }
            spaceService.updateSpaceStatus(id, newStatus);
            redirectAttributes.addFlashAttribute("successMessage", "Bay availability updated to " + newStatus.name() + ".");
            return redirectToStaffSpaces(facilityId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bay availability was not changed: " + safeMessage(e));
            return redirectToStaffSpaces(facilityId);
        }
    }

    private String redirectToStaffSpaces(Long facilityId) {
        return facilityId == null
                ? "redirect:/staff/parking-spaces"
                : "redirect:/staff/parking-spaces?facilityId=" + facilityId;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Please refresh the page and try again." : message;
    }

    @PostMapping({"/staff/parking-spaces/{id}/toggle-status", "/staff/parking-space/{id}/toggle"})
    public String toggleSpaceStatus(
            @PathVariable Long id,
            @RequestParam(required = false) Long facilityId) {
        try {
            staffFacilityAccess.requireSpace(id);
            throw new IllegalArgumentException("Occupancy cannot be toggled manually. Use check-in, check-out, or maintenance actions.");
        } catch (Exception e) {
            return "redirect:/staff/parking-spaces?error=toggle_failed";
        }
    }

    @GetMapping("/staff/occupancy")
    public String viewOccupancy(Model model, @RequestParam(required = false) Long facilityId) {
        try {
            ParkingFacility facility = staffFacilityAccess.requireFacility(facilityId);
            Long targetFacilityId = facility.getId();
            List<ParkingSpace> spaces = spaceService.getFacilityParkingSpaces(targetFacilityId);

            long occupied = spaces.stream()
                    .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED)
                    .count();
            long reserved = spaces.stream()
                    .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.RESERVED)
                    .count();
            long maintenance = spaces.stream()
                    .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.MAINTENANCE)
                    .count();
            long available = spaces.stream()
                    .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE)
                    .count();

            int occPercent = spaces.isEmpty() ? 0 : (int) (occupied * 100 / spaces.size());

            model.addAttribute("facility", facility);
            model.addAttribute("totalSpaces", spaces.size());
            model.addAttribute("occupiedSpaces", occupied);
            model.addAttribute("reservedSpaces", reserved);
            model.addAttribute("maintenanceSpaces", maintenance);
            model.addAttribute("availableSpaces", available);
            model.addAttribute("occupancyPercentage", occPercent);
            model.addAttribute("facilities", List.of(facility));
            model.addAttribute("spaces", spaces);

            return "staff/occupancy";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load occupancy data");
            return "staff/occupancy";
        }
    }
}
