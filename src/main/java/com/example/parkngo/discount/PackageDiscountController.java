package com.example.parkngo.discount;

import com.example.parkngo.discount.dto.DiscountRequest;
import com.example.parkngo.discount.dto.PackageRequest;
import com.example.parkngo.discount.exception.DuplicateResourceException;
import com.example.parkngo.discount.exception.ResourceInUseException;
import com.example.parkngo.discount.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import com.example.parkngo.facility.FacilityServiceRepository;
import com.example.parkngo.facility.ParkingFacilityRepository;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * PackageDiscountController.java
 * Full CRUD for membership packages and promo discount coupons, plus the
 * public customer catalog. Create and Update share a single "/save" handler
 * per package/coupon so the admin form template can stay one file for both
 * modes (an id of null means "create"). Requests are bound to a
 * PackageRequest/DiscountRequest DTO (not the JPA entity directly) and
 * validated with @Valid before the service ever sees them; a validation
 * failure re-renders the same form with inline field errors instead of a
 * generic flash banner.
 */
@Controller
public class PackageDiscountController {

    private static final Logger log = LoggerFactory.getLogger(PackageDiscountController.class);

    private final PackageService packageService;
    private final DiscountService discountService;
    private final ParkingFacilityRepository facilityRepository;
    private final FacilityServiceRepository facilityServiceRepository;

    public PackageDiscountController(PackageService packageService, DiscountService discountService) {
        this(packageService, discountService, null, null);
    }

    @Autowired
    public PackageDiscountController(PackageService packageService, DiscountService discountService,
                                     ParkingFacilityRepository facilityRepository,
                                     FacilityServiceRepository facilityServiceRepository) {
        this.packageService = packageService;
        this.discountService = discountService;
        this.facilityRepository = facilityRepository;
        this.facilityServiceRepository = facilityServiceRepository;
    }

    // ========================= Admin: Packages =========================

    @GetMapping("/admin/packages")
    public String viewPackagesAdmin(Model model) {
        List<Package> packages = packageService.getAllPackages();
        model.addAttribute("packages", packages);
        return "admin/packages";
    }

    @GetMapping("/admin/packages/create")
    public String createPackageForm(Model model) {
        model.addAttribute("package", new PackageRequest());
        model.addAttribute("isEdit", false);
        addPackageFormOptions(model);
        return "admin/package-form";
    }

    @GetMapping("/admin/packages/view/{id}")
    public String viewPackageAdmin(@PathVariable Long id, Model model) {
        try {
            model.addAttribute("package", packageService.getPackageById(id));
            return "admin/package-details";
        } catch (ResourceNotFoundException e) {
            return "redirect:/admin/packages?error=" + urlSafe(e.getMessage());
        }
    }

    @GetMapping("/admin/packages/edit/{id}")
    public String editPackageForm(@PathVariable Long id, Model model) {
        try {
            Package pkg = packageService.getPackageById(id);
            model.addAttribute("package", PackageRequest.fromEntity(pkg));
            model.addAttribute("isEdit", true);
            addPackageFormOptions(model);
            return "admin/package-form";
        } catch (ResourceNotFoundException e) {
            return "redirect:/admin/packages?error=" + urlSafe(e.getMessage());
        }
    }

    @PostMapping("/admin/packages/save")
    public String savePackage(@Valid @ModelAttribute("package") PackageRequest form,
                               BindingResult bindingResult, @org.springframework.web.bind.annotation.RequestParam(required = false) String visitMode, Model model) {
        if ("unlimited".equals(visitMode)) form.setVisitCount(null);
        if ("limited".equals(visitMode) && form.getVisitCount() == null && !bindingResult.hasFieldErrors("visitCount")) {
            bindingResult.rejectValue("visitCount", "required", "Enter the number of visits for a limited pass");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", form.getId() != null);
            model.addAttribute("visitMode", visitMode);
            addPackageFormOptions(model);
            return "admin/package-form";
        }
        try {
            boolean isEdit = form.getId() != null;
            packageService.savePackage(form);
            return "redirect:/admin/packages?success=" + (isEdit ? "Package+updated" : "Package+created");
        } catch (DuplicateResourceException e) {
            bindingResult.rejectValue("name", "duplicate", e.getMessage());
            model.addAttribute("isEdit", form.getId() != null);
            model.addAttribute("visitMode", visitMode);
            addPackageFormOptions(model);
            return "admin/package-form";
        } catch (IllegalArgumentException e) {
            bindingResult.reject("packageAvailability", e.getMessage());
            model.addAttribute("isEdit", form.getId() != null);
            model.addAttribute("visitMode", visitMode);
            addPackageFormOptions(model);
            return "admin/package-form";
        } catch (ResourceNotFoundException e) {
            log.warn("Package save failed: {}", e.getMessage());
            return "redirect:/admin/packages?error=" + urlSafe(e.getMessage());
        }
    }

    private void addPackageFormOptions(Model model) {
        model.addAttribute("facilities", facilityRepository != null ? facilityRepository.findAll() : List.of());
        model.addAttribute("facilityServices", facilityServiceRepository != null
                ? facilityServiceRepository.findAll().stream().filter(com.example.parkngo.facility.FacilityService::isAvailable).toList()
                : List.of());
    }

    @PostMapping("/admin/packages/toggle/{id}")
    public String togglePackage(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            packageService.toggleStatus(id);
            redirectAttributes.addFlashAttribute("success", "Package status updated successfully.");
            return "redirect:/admin/packages";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/packages";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not update package status: " + e.getMessage());
            return "redirect:/admin/packages";
        }
    }

    @PostMapping({"/admin/packages/delete/{id}", "/admin/package/delete/{id}"})
    public String deletePackage(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            packageService.deletePackage(id);
            redirectAttributes.addFlashAttribute("success", "Pass package deleted successfully.");
            return "redirect:/admin/packages";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/packages";
        } catch (ResourceInUseException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/packages";
        } catch (Exception e) {
            log.error("Failed to delete package id={}", id, e);
            redirectAttributes.addFlashAttribute("error", "Package cannot be deleted because it is linked to customer subscriptions. Please deactivate it instead.");
            return "redirect:/admin/packages";
        }
    }

    // ========================= Admin: Discounts =========================

    @GetMapping("/admin/discounts")
    public String viewDiscounts(Model model) {
        List<Discount> discounts = discountService.getAllDiscounts();
        model.addAttribute("discounts", discounts);
        return "admin/discounts";
    }

    @GetMapping("/admin/discounts/create")
    public String createDiscountForm(Model model) {
        model.addAttribute("discount", new DiscountRequest());
        model.addAttribute("isEdit", false);
        return "admin/discount-form";
    }

    @GetMapping("/admin/discounts/edit/{id}")
    public String editDiscountForm(@PathVariable Long id, Model model) {
        try {
            Discount discount = discountService.getDiscountById(id);
            model.addAttribute("discount", DiscountRequest.fromEntity(discount));
            model.addAttribute("isEdit", true);
            return "admin/discount-form";
        } catch (ResourceNotFoundException e) {
            return "redirect:/admin/discounts?error=" + urlSafe(e.getMessage());
        }
    }

    @PostMapping("/admin/discounts/save")
    public String saveDiscount(@Valid @ModelAttribute("discount") DiscountRequest form,
                                BindingResult bindingResult, Model model) {
        // Cross-field rules Bean Validation can't express as simple per-field
        // annotations - checked here (once the individual fields themselves
        // are valid) so they render as inline errors on the relevant field,
        // same as every other validation failure on this form.
        if (!bindingResult.hasFieldErrors("startDate") && !bindingResult.hasFieldErrors("expiryDate")
                && form.getStartDate() != null && form.getExpiryDate() != null
                && form.getExpiryDate().isBefore(form.getStartDate())) {
            bindingResult.rejectValue("expiryDate", "beforeStart", "Expiry date cannot be before the start date");
        }
        if (!bindingResult.hasFieldErrors("value") && form.getDiscountType() == Discount.DiscountType.PERCENTAGE
                && form.getValue() != null && form.getValue().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            bindingResult.rejectValue("value", "max100", "A percentage discount cannot exceed 100%");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("discountTypes", Discount.DiscountType.values());
            model.addAttribute("isEdit", form.getId() != null);
            return "admin/discount-form";
        }
        try {
            boolean isEdit = form.getId() != null;
            discountService.saveDiscount(form);
            return "redirect:/admin/discounts?success=" + (isEdit ? "Discount+code+updated" : "Discount+code+created");
        } catch (DuplicateResourceException e) {
            bindingResult.rejectValue("couponCode", "duplicate", e.getMessage());
            model.addAttribute("discountTypes", Discount.DiscountType.values());
            model.addAttribute("isEdit", form.getId() != null);
            return "admin/discount-form";
        } catch (IllegalArgumentException e) {
            // Belt-and-suspenders: DiscountService re-checks the same
            // cross-field invariants already pre-checked above, in case a
            // future caller reaches saveDiscount() without going through
            // this controller's own checks first.
            bindingResult.reject("invalid", e.getMessage());
            model.addAttribute("discountTypes", Discount.DiscountType.values());
            model.addAttribute("isEdit", form.getId() != null);
            return "admin/discount-form";
        } catch (ResourceNotFoundException e) {
            log.warn("Discount save failed: {}", e.getMessage());
            return "redirect:/admin/discounts?error=" + urlSafe(e.getMessage());
        }
    }

    @PostMapping("/admin/discounts/toggle/{id}")
    public String toggleDiscount(@PathVariable Long id) {
        try {
            discountService.toggleStatus(id);
            return "redirect:/admin/discounts?success=Discount+code+status+updated";
        } catch (ResourceNotFoundException e) {
            return "redirect:/admin/discounts?error=" + urlSafe(e.getMessage());
        }
    }

    @PostMapping({"/admin/discounts/delete/{id}", "/admin/discount/delete/{id}"})
    public String deleteDiscount(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            discountService.deleteDiscount(id);
            redirectAttributes.addFlashAttribute("success", "Discount code deleted successfully.");
            return "redirect:/admin/discounts";
        } catch (ResourceInUseException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/discounts";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete discount code: " + e.getMessage());
            return "redirect:/admin/discounts";
        }
    }

    // ========================= Customer: Packages Catalog =========================

    @GetMapping("/customer/packages")
    public String viewPackagesCustomer(Model model) {
        List<Package> packages = packageService.getActivePackages();
        model.addAttribute("packages", packages);
        return "customer/packages";
    }

    @GetMapping("/customer/package/{id}")
    public String viewPackageDetails(@PathVariable Long id, Model model) {
        try {
            Package pkg = packageService.getPackageById(id);
            if (!pkg.isActive()) return "redirect:/customer/packages";
            model.addAttribute("package", pkg);
            return "customer/package-details";
        } catch (ResourceNotFoundException e) {
            return "redirect:/customer/packages?error=" + urlSafe(e.getMessage());
        }
    }

    /**
     * Percent-encodes free text for safe embedding in a redirect query
     * string. Previously only spaces were replaced, so any other special
     * character in an exception message (brackets, backticks, parentheses,
     * newlines) landed unescaped in the URL and could make Tomcat reject the
     * whole redirect with a raw "Invalid character found in the request
     * target" page instead of a clean flash-message banner.
     */
    private String urlSafe(String text) {
        if (text == null) return "Unknown+error";
        String singleLine = text.replaceAll("\\s+", " ").trim();
        if (singleLine.length() > 200) {
            singleLine = singleLine.substring(0, 200) + "...";
        }
        return URLEncoder.encode(singleLine, StandardCharsets.UTF_8);
    }
}
