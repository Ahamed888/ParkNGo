package com.example.parkngo.user;

import com.example.parkngo.util.SecurityUtil;
import com.example.parkngo.util.UserInputValidator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class UserController {
    private static final Set<String> PROFILE_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private final UserService userService;
    private final RoleRepository roleRepository;
    private final SecurityUtil securityUtil;

    public UserController(UserService userService, RoleRepository roleRepository, SecurityUtil securityUtil) {
        this.userService = userService;
        this.roleRepository = roleRepository;
        this.securityUtil = securityUtil;
    }

    // --- Admin User Management ---

    @GetMapping("/admin/users")
    public String viewUsers(
            Model model,
            @RequestParam(required = false, defaultValue = "ALL") String role,
            @RequestParam(required = false) String search) {

        List<User> users = userService.filterUsers(role, search);
        List<User> allUsers = userService.getAllUsers();

        long totalCount = allUsers.size();
        long customerCount = allUsers.stream().filter(u -> u.getRole() != null && "CUSTOMER".equalsIgnoreCase(u.getRole().getName())).count();
        long staffCount = allUsers.stream().filter(u -> u.getRole() != null && "STAFF".equalsIgnoreCase(u.getRole().getName())).count();
        long adminCount = allUsers.stream().filter(u -> u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getName())).count();

        model.addAttribute("users", users);
        model.addAttribute("selectedRole", role);
        model.addAttribute("searchQuery", search);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("customerCount", customerCount);
        model.addAttribute("staffCount", staffCount);
        model.addAttribute("adminCount", adminCount);

        return "admin/users";
    }

    @GetMapping("/admin/user/add")
    public String addUserForm(Model model) {
        model.addAttribute("user", new User());
        List<Role> roles = roleRepository.findAll();
        model.addAttribute("roles", roles);
        populateFacilityAssignmentModel(model, null, null);
        model.addAttribute("isEdit", false);
        return "admin/user-form";
    }

    @PostMapping("/admin/user/add")
    public String addUser(User user, @RequestParam String roleName,
                          @RequestParam(required = false) Long facilityId, Model model) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (!UserInputValidator.isValidName(user.getFullName())) {
            fieldErrors.put("fullName", "Enter a valid full name between 2 and 60 characters.");
        }
        if (!UserInputValidator.isValidEmail(user.getEmail())) {
            fieldErrors.put("email", "Please provide a valid email address (e.g. user@example.com).");
        }
        if (!UserInputValidator.isValidSriLankanMobile(user.getPhone())) {
            fieldErrors.put("phone", "Please provide a valid Sri Lankan phone number (e.g. 0771234567 or +94771234567).");
        }
        if (!UserInputValidator.isStrongPassword(user.getPassword())) {
            fieldErrors.put("password", "Password must be 8-72 characters and include uppercase, lowercase, a number, and a special character.");
        }
        if (user.getShiftHours() != null && user.getShiftHours().length() > 50) {
            fieldErrors.put("shiftHours", "Shift hours must be 50 characters or fewer.");
        }
        validateFacilityAssignment(roleName, facilityId, fieldErrors);
        if (!fieldErrors.isEmpty()) {
            return showAdminUserForm(model, user, roleName, facilityId, false, fieldErrors, null);
        }
        try {
            userService.registerUser(user, roleName, facilityId);
            return "redirect:/admin/users?success=added";
        } catch (IllegalArgumentException e) {
            if ("STAFF".equalsIgnoreCase(roleName)) {
                fieldErrors.put("facilityId", e.getMessage());
            }
            return showAdminUserForm(model, user, roleName, facilityId, false, fieldErrors,
                    "The staff facility assignment could not be saved.");
        } catch (Exception e) {
            return showAdminUserForm(model, user, roleName, facilityId, false, fieldErrors,
                    "The user account could not be created. Check that the email and role are available.");
        }
    }

    @GetMapping("/admin/user/edit/{id}")
    public String editUserForm(@PathVariable Long id, Model model) {
        try {
            User user = userService.getUserById(id);
            List<Role> roles = roleRepository.findAll();
            model.addAttribute("user", user);
            model.addAttribute("roles", roles);
            populateFacilityAssignmentModel(model, user, null);
            model.addAttribute("isEdit", true);
            return "admin/user-form";
        } catch (Exception e) {
            return "redirect:/admin/users?error=notfound";
        }
    }

    @PostMapping("/admin/user/edit/{id}")
    public String editUser(
            @PathVariable Long id,
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam String roleName,
            @RequestParam(required = false) User.UserStatus status,
            @RequestParam(required = false) String shiftHours,
            @RequestParam(required = false) String newPassword,
            @RequestParam(required = false) Long facilityId,
            Model model) {
        User submitted = new User();
        submitted.setId(id);
        submitted.setFullName(fullName);
        submitted.setEmail(email);
        submitted.setPhone(phone);
        submitted.setStatus(status);
        submitted.setShiftHours(shiftHours);
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (!UserInputValidator.isValidName(fullName)) {
            fieldErrors.put("fullName", "Enter a valid full name between 2 and 60 characters.");
        }
        if (!UserInputValidator.isValidEmail(email)) {
            fieldErrors.put("email", "Please provide a valid email address.");
        }
        if (!UserInputValidator.isValidSriLankanMobile(phone)) {
            fieldErrors.put("phone", "Enter a valid Sri Lankan mobile number, for example 077 123 4567.");
        }
        if (newPassword != null && !newPassword.isBlank() && !UserInputValidator.isStrongPassword(newPassword)) {
            fieldErrors.put("newPassword", "New password must be 8-72 characters and include uppercase, lowercase, a number, and a special character.");
        }
        if (shiftHours != null && shiftHours.length() > 50) {
            fieldErrors.put("shiftHours", "Shift hours must be 50 characters or fewer.");
        }
        validateFacilityAssignment(roleName, facilityId, fieldErrors);
        if (!fieldErrors.isEmpty()) {
            return showAdminUserForm(model, submitted, roleName, facilityId, true, fieldErrors, null);
        }
        try {
            userService.adminUpdateUser(id, fullName, email, phone, roleName, status, shiftHours, newPassword, facilityId);
            return "redirect:/admin/users?success=updated";
        } catch (IllegalArgumentException e) {
            if ("STAFF".equalsIgnoreCase(roleName)) {
                fieldErrors.put("facilityId", e.getMessage());
            }
            return showAdminUserForm(model, submitted, roleName, facilityId, true, fieldErrors,
                    "The staff facility assignment could not be updated.");
        } catch (Exception e) {
            return showAdminUserForm(model, submitted, roleName, facilityId, true, fieldErrors,
                    "The user account could not be updated. Check that the email and role are valid.");
        }
    }

    private void validateFacilityAssignment(String roleName, Long facilityId, Map<String, String> fieldErrors) {
        try {
            userService.requireActiveStaffFacility(roleName, facilityId);
        } catch (IllegalArgumentException e) {
            fieldErrors.put("facilityId", e.getMessage());
        }
    }

    private void populateFacilityAssignmentModel(Model model, User displayedUser, Long submittedFacilityId) {
        model.addAttribute("activeFacilities", userService.getActiveParkingFacilities());
        model.addAttribute("submittedFacilityId", submittedFacilityId);
        User persistedUser = displayedUser;
        if (!(persistedUser instanceof Staff) && displayedUser != null && displayedUser.getId() != null) {
            try {
                persistedUser = userService.getUserById(displayedUser.getId());
            } catch (Exception ignored) {
                persistedUser = displayedUser;
            }
        }
        model.addAttribute("currentAssignedFacility",
                persistedUser instanceof Staff staff ? staff.getAssignedFacility() : null);
    }

    private String showAdminUserForm(Model model, User user, String roleName, Long facilityId, boolean isEdit,
                                     Map<String, String> fieldErrors, String formError) {
        model.addAttribute("user", user);
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("submittedRoleName", roleName);
        populateFacilityAssignmentModel(model, user, facilityId);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("error", formError != null ? formError : "Please correct the highlighted fields.");
        return "admin/user-form";
    }

    @PostMapping("/admin/user/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id) {
        try {
            userService.toggleUserStatus(id);
            return "redirect:/admin/users?success=status_updated";
        } catch (Exception e) {
            return "redirect:/admin/users?error=toggle_failed";
        }
    }

    @PostMapping({"/admin/user/delete/{id}", "/admin/users/delete/{id}"})
    public String deleteUser(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            User current = securityUtil.getCurrentUser();
            if (current != null && current.getId().equals(id)) {
                redirectAttributes.addFlashAttribute("error", "You cannot delete your own admin account.");
                return "redirect:/admin/users";
            }
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("success", "User account deleted successfully.");
            return "redirect:/admin/users";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete user: " + e.getMessage());
            return "redirect:/admin/users";
        }
    }

    // --- Customer Profile Management ---

    @GetMapping("/customer/profile")
    public String viewProfile(Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";
        model.addAttribute("user", user);
        return "customer/profile";
    }

    @PostMapping("/customer/profile")
    public String updateProfile(
            @RequestParam String fullName,
            @RequestParam String phone,
            @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
            @RequestParam(value = "photoUrl", required = false) String photoUrl,
            @RequestParam(value = "removePhoto", required = false, defaultValue = "false") boolean removePhoto,
            Model model) {
        try {
            Long currentUserId = securityUtil.getCurrentUserId();
            if (currentUserId == null) return "redirect:/login";
            model.addAttribute("submittedFullName", fullName);
            model.addAttribute("submittedPhone", phone);
            model.addAttribute("submittedPhotoUrl", photoUrl);

            if (!UserInputValidator.isValidName(fullName)) {
                model.addAttribute("profileFieldErrors", Map.of("fullName", "Enter a valid full name between 2 and 60 characters."));
                model.addAttribute("error", "Please correct the highlighted field.");
                model.addAttribute("user", securityUtil.getCurrentUser());
                return "customer/profile";
            }
            if (!UserInputValidator.isValidSriLankanMobile(phone)) {
                model.addAttribute("profileFieldErrors", Map.of("phone", "Please provide a valid Sri Lankan phone number (e.g. 0771234567 or +94771234567)."));
                model.addAttribute("error", "Please correct the highlighted field.");
                model.addAttribute("user", securityUtil.getCurrentUser());
                return "customer/profile";
            }

            String profilePictureToSet = null;
            if (removePhoto) {
                profilePictureToSet = "";
            } else if (photoFile != null && !photoFile.isEmpty()) {
                if (photoFile.getSize() > 5 * 1024 * 1024) {
                    model.addAttribute("profileFieldErrors", Map.of("photoFile", "Profile photo size must not exceed 5MB."));
                    model.addAttribute("error", "Please correct the highlighted field.");
                    model.addAttribute("user", securityUtil.getCurrentUser());
                    return "customer/profile";
                }
                String contentType = photoFile.getContentType();
                if (contentType == null || !PROFILE_IMAGE_TYPES.contains(contentType.toLowerCase())) {
                    model.addAttribute("profileFieldErrors", Map.of("photoFile", "Only image files (JPEG, PNG, WebP, GIF) are allowed."));
                    model.addAttribute("error", "Please correct the highlighted field.");
                    model.addAttribute("user", securityUtil.getCurrentUser());
                    return "customer/profile";
                }
                profilePictureToSet = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(photoFile.getBytes());
            } else if (photoUrl != null && !photoUrl.isBlank()) {
                String cleanUrl = photoUrl.trim();
                if (cleanUrl.length() > 2000 || !(cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://"))) {
                    model.addAttribute("profileFieldErrors", Map.of("photoUrl", "Enter a complete profile image URL beginning with http:// or https://."));
                    model.addAttribute("error", "Please correct the highlighted field.");
                    model.addAttribute("user", securityUtil.getCurrentUser());
                    return "customer/profile";
                }
                profilePictureToSet = cleanUrl;
            }

            User updated = userService.updateProfile(currentUserId, fullName, phone, profilePictureToSet);
            model.asMap().remove("submittedFullName");
            model.asMap().remove("submittedPhone");
            model.asMap().remove("submittedPhotoUrl");
            model.addAttribute("success", "Profile details and photo updated successfully!");
            model.addAttribute("user", updated);
            return "customer/profile";
        } catch (Exception e) {
            model.addAttribute("error", e instanceof IllegalArgumentException
                    ? e.getMessage() : "We could not update your profile. Please try again.");
            model.addAttribute("user", securityUtil.getCurrentUser());
            return "customer/profile";
        }
    }

    @PostMapping("/customer/profile/change-password")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Model model) {
        try {
            Long currentUserId = securityUtil.getCurrentUserId();
            if (currentUserId == null) return "redirect:/login";

            if (!newPassword.equals(confirmPassword)) {
                model.addAttribute("passwordFieldErrors", Map.of("confirmPassword", "New password and confirmation do not match."));
                model.addAttribute("passwordError", "Please correct the highlighted field.");
                model.addAttribute("user", securityUtil.getCurrentUser());
                return "customer/profile";
            }

            if (!UserInputValidator.isStrongPassword(newPassword)) {
                model.addAttribute("passwordFieldErrors", Map.of("newPassword", "New password must be 8-72 characters and include uppercase, lowercase, a number, and a special character."));
                model.addAttribute("passwordError", "Please correct the highlighted field.");
                model.addAttribute("user", securityUtil.getCurrentUser());
                return "customer/profile";
            }

            userService.changePassword(currentUserId, currentPassword, newPassword);
            model.addAttribute("passwordSuccess", "Your password has been changed successfully!");
            model.addAttribute("user", securityUtil.getCurrentUser());
            return "customer/profile";
        } catch (Exception e) {
            if ("Current password does not match".equals(e.getMessage())) {
                model.addAttribute("passwordFieldErrors", Map.of("currentPassword", "Current password does not match."));
                model.addAttribute("passwordError", "Please correct the highlighted field.");
            } else {
                model.addAttribute("passwordError", "Your password could not be changed. Please try again.");
            }
            model.addAttribute("user", securityUtil.getCurrentUser());
            return "customer/profile";
        }
    }
}
