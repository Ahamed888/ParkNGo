package com.example.parkngo.feedback;

import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationService;
import com.example.parkngo.user.User;
import com.example.parkngo.util.SecurityUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class FeedbackNotificationController {
    private final FeedbackService feedbackService;
    private final NotificationService notificationService;
    private final ReservationService reservationService;
    private final SecurityUtil securityUtil;

    public FeedbackNotificationController(FeedbackService feedbackService,
                                          NotificationService notificationService,
                                          ReservationService reservationService,
                                          SecurityUtil securityUtil) {
        this.feedbackService = feedbackService;
        this.notificationService = notificationService;
        this.reservationService = reservationService;
        this.securityUtil = securityUtil;
    }

    // --- Customer Feedback Management ---

    @GetMapping("/customer/feedback")
    public String viewFeedbackCustomer(Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        List<Feedback> feedback = feedbackService.getCustomerFeedback(user.getId());
        model.addAttribute("feedback", feedback);
        return "customer/feedback";
    }

    @GetMapping("/customer/feedback/submit/{reservationId}")
    public String submitFeedbackForm(@PathVariable Long reservationId, Model model, RedirectAttributes redirectAttributes) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        try {
            Reservation reservation = reservationService.getReservationById(reservationId);
            if (reservation.getCustomer() == null || !user.getId().equals(reservation.getCustomer().getId())
                    || reservation.getStatus() != Reservation.ReservationStatus.COMPLETED) {
                throw new Exception("This reservation is not available for feedback");
            }
            if (feedbackService.getCustomerFeedback(user.getId()).stream()
                    .anyMatch(item -> item.getReservation() != null && reservationId.equals(item.getReservation().getId()))) {
                redirectAttributes.addFlashAttribute("error", "Feedback has already been submitted for this reservation.");
                return "redirect:/customer/feedback";
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/feedback";
        }

        model.addAttribute("reservationId", reservationId);
        model.addAttribute("feedback", new Feedback());
        model.addAttribute("mode", "create");
        return "customer/feedback-form";
    }

    @GetMapping("/customer/feedback/edit/{id}")
    public String editFeedbackForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        try {
            Feedback feedback = feedbackService.getCustomerFeedbackById(id, user.getId());
            if (feedback.getAdminResponse() != null && !feedback.getAdminResponse().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Feedback cannot be edited after management has responded.");
                return "redirect:/customer/feedback";
            }
            model.addAttribute("feedback", feedback);
            model.addAttribute("mode", "edit");
            return "customer/feedback-form";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Feedback not found or unauthorized.");
            return "redirect:/customer/feedback";
        }
    }

    @PostMapping("/customer/feedback/submit")
    public String submitFeedback(Feedback feedback, @RequestParam Long reservationId, Model model) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return "redirect:/login";

            Map<String, String> fieldErrors = feedbackFieldErrors(feedback.getRating(), feedback.getComment());
            if (!fieldErrors.isEmpty()) {
                return showCustomerFeedbackForm(model, feedback, "create", reservationId, fieldErrors);
            }

            Reservation reservation = reservationService.getReservationById(reservationId);
            if (reservation.getCustomer() == null || !user.getId().equals(reservation.getCustomer().getId())
                    || reservation.getStatus() != Reservation.ReservationStatus.COMPLETED) {
                throw new Exception("This reservation is not available for feedback");
            }

            feedback.setCustomer(user);
            feedback.setReservation(reservation);

            feedbackService.submitFeedback(feedback);
            return "redirect:/customer/feedback?success=submitted";
        } catch (Exception e) {
            model.addAttribute("error", "Feedback could not be submitted. Confirm that the completed reservation is yours and has not already been reviewed.");
            model.addAttribute("mode", "create");
            model.addAttribute("reservationId", reservationId);
            model.addAttribute("feedback", feedback);
            return "customer/feedback-form";
        }
    }

    @PostMapping("/customer/feedback/edit/{id}")
    public String updateFeedback(@PathVariable Long id,
                                 Feedback feedback,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        Map<String, String> fieldErrors = feedbackFieldErrors(feedback.getRating(), feedback.getComment());
        if (!fieldErrors.isEmpty()) {
            feedback.setId(id);
            return showCustomerFeedbackForm(model, feedback, "edit", null, fieldErrors);
        }
        try {
            feedbackService.updateCustomerFeedback(id, user.getId(), feedback.getRating(), feedback.getComment());
            redirectAttributes.addFlashAttribute("success", "Feedback updated successfully.");
            return "redirect:/customer/feedback";
        } catch (Exception e) {
            feedback.setId(id);
            model.addAttribute("error", "Feedback could not be updated. It may no longer be editable.");
            model.addAttribute("feedback", feedback);
            model.addAttribute("mode", "edit");
            return "customer/feedback-form";
        }
    }

    @PostMapping("/customer/feedback/delete/{id}")
    public String deleteFeedback(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        try {
            feedbackService.deleteCustomerFeedback(id, user.getId());
            redirectAttributes.addFlashAttribute("success", "Feedback deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customer/feedback";
    }

    // --- Admin Feedback Management ---

    @GetMapping("/admin/feedback")
    public String viewFeedbackAdmin(Model model) {
        List<Feedback> feedback = feedbackService.getAllFeedback();
        model.addAttribute("feedback", feedback);
        return "admin/feedback";
    }

    @PostMapping("/admin/feedback/{id}/respond")
    public String respondToFeedback(@PathVariable Long id, @RequestParam String response, RedirectAttributes redirectAttributes) {
        String cleanResponse = response == null ? "" : response.trim();
        if (cleanResponse.isEmpty() || cleanResponse.length() > 2000) {
            redirectAttributes.addFlashAttribute("responseErrorId", id);
            redirectAttributes.addFlashAttribute("responseError", cleanResponse.isEmpty()
                    ? "Response is required." : "Response must be 2000 characters or fewer.");
            redirectAttributes.addFlashAttribute("submittedResponse", response);
            return "redirect:/admin/feedback";
        }
        try {
            feedbackService.respondToFeedback(id, cleanResponse);
            return "redirect:/admin/feedback?success=responded";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "The response could not be saved. Please refresh and try again.");
            return "redirect:/admin/feedback";
        }
    }

    private Map<String, String> feedbackFieldErrors(Integer rating, String comment) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (rating == null || rating < 1 || rating > 5) {
            errors.put("rating", "Please select a rating between 1 and 5 stars.");
        }
        int commentLength = comment == null ? 0 : comment.trim().length();
        if (commentLength < 3 || commentLength > 2000) {
            errors.put("comment", "Comment must be between 3 and 2000 characters.");
        }
        return errors;
    }

    private String showCustomerFeedbackForm(Model model, Feedback feedback, String mode, Long reservationId,
                                            Map<String, String> fieldErrors) {
        model.addAttribute("feedback", feedback);
        model.addAttribute("mode", mode);
        model.addAttribute("reservationId", reservationId);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("error", "Please correct the highlighted fields.");
        return "customer/feedback-form";
    }

    @PostMapping("/admin/feedback/delete/{id}")
    public String deleteFeedbackByAdmin(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "Feedback records cannot be deleted by administrator.");
        return "redirect:/admin/feedback";
    }

    // --- Customer Notifications ---

    @GetMapping("/customer/notifications")
    public String viewNotifications(Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        List<Notification> notifications = notificationService.getUserNotifications(user.getId());
        model.addAttribute("notifications", notifications);
        return "customer/notifications";
    }

    @PostMapping("/customer/notification/{id}/read")
    public String markNotificationAsRead(@PathVariable Long id) {
        try {
            notificationService.markAsRead(id);
            return "redirect:/customer/notifications";
        } catch (Exception e) {
            return "redirect:/customer/notifications";
        }
    }
}
