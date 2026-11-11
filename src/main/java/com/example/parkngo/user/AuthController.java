package com.example.parkngo.user;

import com.example.parkngo.util.UserInputValidator;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class AuthController {
    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;

    public AuthController(UserService userService,
                          UserRepository userRepository,
                          PasswordResetService passwordResetService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestPasswordReset(@RequestParam(required = false) String email, Model model) {
        String cleanEmail = UserInputValidator.cleanEmail(email);
        if (!UserInputValidator.isValidEmail(cleanEmail)) {
            model.addAttribute("email", cleanEmail);
            model.addAttribute("fieldErrors", Map.of("email", "Please provide a valid email address."));
            return "auth/forgot-password";
        }

        try {
            passwordResetService.requestPasswordReset(cleanEmail);
        } catch (RuntimeException ignored) {
            // Keep the response neutral so account existence and delivery state are not disclosed.
        }
        return "redirect:/forgot-password?sent";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam(required = false) String token, Model model) {
        boolean tokenValid = passwordResetService.isTokenValid(token);
        model.addAttribute("tokenValid", tokenValid);
        if (tokenValid) {
            model.addAttribute("token", token);
        }
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam(required = false) String token,
                                @RequestParam(required = false) String newPassword,
                                @RequestParam(required = false) String confirmPassword,
                                Model model) {
        if (!passwordResetService.isTokenValid(token)) {
            model.addAttribute("tokenValid", false);
            return "auth/reset-password";
        }

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (!UserInputValidator.isStrongPassword(newPassword)) {
            fieldErrors.put("newPassword", "Password must be 8-72 characters and include uppercase, lowercase, a number, and a special character.");
        }
        if (confirmPassword == null || !confirmPassword.equals(newPassword)) {
            fieldErrors.put("confirmPassword", "Passwords do not match. Please re-enter.");
        }
        if (!fieldErrors.isEmpty()) {
            model.addAttribute("tokenValid", true);
            model.addAttribute("token", token);
            model.addAttribute("fieldErrors", fieldErrors);
            return "auth/reset-password";
        }

        try {
            passwordResetService.resetPassword(token, newPassword, confirmPassword);
            return "redirect:/login?resetSuccess";
        } catch (InvalidPasswordResetTokenException exception) {
            model.addAttribute("tokenValid", false);
            return "auth/reset-password";
        } catch (Exception exception) {
            model.addAttribute("tokenValid", true);
            model.addAttribute("token", token);
            model.addAttribute("error", "We could not reset your password. Please request a new reset link.");
            return "auth/reset-password";
        }
    }

    @GetMapping("/logout/goodbye")
    public String logoutGoodbye(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        return "auth/logout-goodbye";
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        model.addAttribute("user", new User());
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute User user,
                               @RequestParam(required = false) String password,
                               @RequestParam(required = false) String confirmPassword,
                               Model model) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // 1. Full Name Validation
        if (user.getFullName() == null || user.getFullName().trim().isEmpty()) {
            fieldErrors.put("fullName", "Full name is required.");
        } else {
            String cleanName = UserInputValidator.cleanName(user.getFullName());
            if (cleanName.length() < 2 || cleanName.length() > 60) {
                fieldErrors.put("fullName", "Full name must be between 2 and 60 characters.");
            } else if (!UserInputValidator.isValidName(cleanName)) {
                fieldErrors.put("fullName", "Enter a valid full name using letters, spaces, apostrophes, periods, or hyphens.");
            }
        }

        // 2. Email Validation (e.g. Gmail or valid email domain)
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            fieldErrors.put("email", "Email address is required (e.g. yourname@gmail.com).");
        } else {
            String cleanEmail = UserInputValidator.cleanEmail(user.getEmail());
            if (cleanEmail.length() > 100) {
                fieldErrors.put("email", "Email address cannot exceed 100 characters.");
            } else if (!UserInputValidator.isValidEmail(cleanEmail)) {
                fieldErrors.put("email", "Please provide a valid email address (e.g. yourname@gmail.com).");
            } else if (userRepository.existsByEmail(cleanEmail)) {
                fieldErrors.put("email", "An account with this email address already exists. Please sign in instead.");
            }
        }

        // 3. Mobile Phone Number Validation
        if (user.getPhone() == null || user.getPhone().trim().isEmpty()) {
            fieldErrors.put("phone", "Mobile phone number is required.");
        } else {
            String cleanPhone = UserInputValidator.cleanPhone(user.getPhone());
            if (!UserInputValidator.isValidSriLankanMobile(cleanPhone)) {
                fieldErrors.put("phone", "Please provide a valid Sri Lankan mobile number (e.g. 0771234567 or +94771234567).");
            }
        }

        // 4. Password & Confirmation Validation
        if (password == null || password.isEmpty()) {
            fieldErrors.put("password", "Password is required.");
        } else if (!UserInputValidator.isStrongPassword(password)) {
            fieldErrors.put("password", "Password must be 8-72 characters and include uppercase, lowercase, a number, and a special character.");
        } else if (confirmPassword == null || !password.equals(confirmPassword)) {
            fieldErrors.put("confirmPassword", "Passwords do not match. Please re-enter.");
        }

        if (!fieldErrors.isEmpty()) {
            model.addAttribute("user", user);
            model.addAttribute("fieldErrors", fieldErrors);
            model.addAttribute("error", "Please correct the highlighted fields and try again.");
            return "auth/register";
        }

        try {
            user.setPassword(password);
            user.setFullName(UserInputValidator.cleanName(user.getFullName()));
            user.setEmail(UserInputValidator.cleanEmail(user.getEmail()));
            user.setPhone(UserInputValidator.cleanPhone(user.getPhone()));
            userService.registerUser(user, "CUSTOMER");

            return "redirect:/login?success";
        } catch (Exception e) {
            model.addAttribute("user", user);
            model.addAttribute("error", "We could not create your account. Check your details and try again.");
            return "auth/register";
        }
    }
}
