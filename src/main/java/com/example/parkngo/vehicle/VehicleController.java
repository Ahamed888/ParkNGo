package com.example.parkngo.vehicle;

import com.example.parkngo.user.User;
import com.example.parkngo.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/customer")
public class VehicleController {
    private final VehicleService vehicleService;
    private final SecurityUtil securityUtil;

    public VehicleController(VehicleService vehicleService, SecurityUtil securityUtil) {
        this.vehicleService = vehicleService;
        this.securityUtil = securityUtil;
    }

    @GetMapping("/vehicles")
    public String viewVehicles(Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }

        List<Vehicle> vehicles = vehicleService.getCustomerVehicles(user.getId());
        model.addAttribute("vehicles", vehicles);
        model.addAttribute("totalVehicles", vehicles.size());
        return "customer/vehicles";
    }

    @GetMapping({"/vehicles/add", "/vehicle/add"})
    public String addVehicleForm(Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }

        if (!model.containsAttribute("vehicle")) {
            Vehicle vehicle = new Vehicle();
            vehicle.setVehicleType("SEDAN");
            model.addAttribute("vehicle", vehicle);
        }
        model.addAttribute("mode", "add");
        return "customer/vehicle-form";
    }

    @PostMapping({"/vehicles/add", "/vehicle/add"})
    public String addVehicle(@Valid @ModelAttribute("vehicle") Vehicle vehicle,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        User user = securityUtil.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "add");
            return "customer/vehicle-form";
        }

        try {
            vehicle.setCustomer(user);
            vehicleService.registerVehicle(vehicle);
            redirectAttributes.addFlashAttribute("success", "Vehicle '" + vehicle.getRegistrationNumber() + "' registered successfully!");
            return "redirect:/customer/vehicles";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("mode", "add");
            return "customer/vehicle-form";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to register vehicle: " + e.getMessage());
            model.addAttribute("mode", "add");
            return "customer/vehicle-form";
        }
    }

    @GetMapping({"/vehicles/edit/{id}", "/vehicle/edit/{id}"})
    public String editVehicleForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        User user = securityUtil.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }

        try {
            Vehicle vehicle = vehicleService.getVehicleByIdAndCustomer(id, user.getId());
            model.addAttribute("vehicle", vehicle);
            model.addAttribute("mode", "edit");
            return "customer/vehicle-form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Vehicle not found or unauthorized.");
            return "redirect:/customer/vehicles";
        }
    }

    @PostMapping({"/vehicles/edit/{id}", "/vehicle/edit/{id}"})
    public String editVehicle(@PathVariable Long id,
                              @Valid @ModelAttribute("vehicle") Vehicle vehicle,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        User user = securityUtil.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }

        vehicle.setId(id);

        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            return "customer/vehicle-form";
        }

        try {
            vehicleService.updateVehicle(vehicle, user.getId());
            redirectAttributes.addFlashAttribute("success", "Vehicle details updated successfully!");
            return "redirect:/customer/vehicles";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("mode", "edit");
            return "customer/vehicle-form";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to update vehicle: " + e.getMessage());
            model.addAttribute("mode", "edit");
            return "customer/vehicle-form";
        }
    }

    @PostMapping({"/vehicles/delete/{id}", "/vehicle/delete/{id}"})
    public String deleteVehicle(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = securityUtil.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }

        try {
            vehicleService.deleteVehicle(id, user.getId());
            redirectAttributes.addFlashAttribute("success", "Vehicle removed successfully.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Unable to remove this vehicle. Please ensure there are no active parking reservations for this vehicle.");
        }
        return "redirect:/customer/vehicles";
    }
}
