package com.example.parkngo.payment;

import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationRepository;
import com.example.parkngo.reservation.ReservationService;
import com.example.parkngo.user.User;
import com.example.parkngo.util.SecurityUtil;
import com.example.parkngo.feedback.Notification;
import com.example.parkngo.feedback.NotificationService;
import com.example.parkngo.pattern.observer.ParkingEvent;
import com.example.parkngo.pattern.observer.ParkingEventSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final SecurityUtil securityUtil;
    private final NotificationService notificationService;

    @Autowired
    public PaymentController(PaymentService paymentService,
                              ReservationRepository reservationRepository,
                              ReservationService reservationService,
                              SecurityUtil securityUtil,
                              @Autowired(required = false) NotificationService notificationService) {
        this.paymentService = paymentService;
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
        this.securityUtil = securityUtil;
        this.notificationService = notificationService;
    }

    // --- Customer Checkout Screen (the payment step of an active reservation checkout) ---

    @GetMapping("/customer/payment")
    public String checkoutScreen(@RequestParam(required = false) Long reservationId,
                                  @RequestParam(required = false) BigDecimal amount,
                                  @RequestParam(required = false) String facilityName,
                                  @RequestParam(required = false) String spaceNumber,
                                  Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        String duration = "-";
        if (reservationId != null) {
            Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
            if (reservation == null || reservation.getCustomer() == null
                    || !reservation.getCustomer().getId().equals(user.getId())) {
                return "redirect:/customer/reservations?error=You+do+not+own+this+reservation";
            }
            amount = reservation.getStatus() == Reservation.ReservationStatus.OVERSTAY_PAYMENT_DUE
                    && reservation.getOverstayCharge() != null
                    ? reservation.getOverstayCharge() : reservation.getTotalAmount();
            facilityName = reservation.getParkingSpace().getFacility().getName();
            spaceNumber = reservation.getParkingSpace().getSpaceNumber();
            long hours = Math.max(1, Duration.between(reservation.getArrivalTime(), reservation.getDepartureTime()).toMinutes() / 60);
            duration = hours + ".0 hrs";
        }

        model.addAttribute("reservationId", reservationId);
        model.addAttribute("amount", amount != null ? amount : BigDecimal.valueOf(500.00));
        model.addAttribute("facilityName", facilityName != null ? facilityName : "ParkNGo Facility");
        model.addAttribute("spaceNumber", spaceNumber != null ? spaceNumber : "-");
        model.addAttribute("duration", duration);
        return "customer/payment";
    }

    // --- Customer Payment History / Ledger ---

    @GetMapping("/customer/payments")
    public String customerPaymentHistory(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<Payment> paymentPage = paymentService.getPaymentsForUser(user.getId(), pageable);
        BigDecimal totalSpent = paymentService.getTotalSpentForUser(user.getId());

        model.addAttribute("payments", paymentPage.getContent());
        model.addAttribute("totalSpent", totalSpent);
        model.addAttribute("totalTransactions", paymentPage.getTotalElements());
        model.addAttribute("totalPages", paymentPage.getTotalPages());
        model.addAttribute("currentPage", paymentPage.getNumber());
        model.addAttribute("pageSize", size);
        return "customer/payments";
    }

    // --- Simulated Gateway Endpoint ---

    @PostMapping("/payment/process")
    public String processPayment(@RequestParam(required = false) Long reservationId,
                                  @RequestParam BigDecimal amount,
                                  @RequestParam String paymentMethod,
                                  @RequestParam(required = false) String cardNumber,
                                  @RequestParam(required = false) String cardholderName,
                                  @RequestParam(required = false) String expiryDate,
                                  @RequestParam(required = false) String cvc,
                                  @RequestHeader(value = "Referer", required = false) String referer,
                                  RedirectAttributes redirectAttributes) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        String backTarget;
        if (referer != null && referer.contains("/customer/payments")) {
            backTarget = "/customer/payments";
        } else if (reservationId != null) {
            backTarget = "/customer/reservation/" + reservationId;
        } else {
            backTarget = "/customer/dashboard";
        }

        try {
            Payment payment;

            if ("CREDIT_CARD".equals(paymentMethod) || "DEBIT_CARD".equals(paymentMethod)) {
                PaymentService.CardValidationError cardError = paymentService.validateCardFields(cardholderName, cardNumber, expiryDate, cvc);
                if (cardError != null) {
                    redirectAttributes.addFlashAttribute("paymentFieldErrors", Map.of(cardError.field(), cardError.message()));
                    redirectAttributes.addFlashAttribute("error", "Please correct the highlighted card field.");
                    return reservationId == null
                            ? "redirect:/customer/payment"
                            : "redirect:/customer/payment?reservationId=" + reservationId;
                }
            }

            if (reservationId != null) {
                Reservation reservation = reservationRepository.findById(reservationId)
                        .orElseThrow(() -> new Exception("Reservation not found"));
                if (reservation.getCustomer() == null || !reservation.getCustomer().getId().equals(user.getId())) {
                    return "redirect:/customer/reservations?error=You+do+not+own+this+reservation";
                }
                // The reservation total is server-calculated.  Do not clamp a
                // client-supplied amount: clamping would turn an underpayment
                // (including zero) into a successful payment.
                BigDecimal expectedAmount = reservation.getStatus() == Reservation.ReservationStatus.OVERSTAY_PAYMENT_DUE
                        && reservation.getOverstayCharge() != null
                        ? reservation.getOverstayCharge() : reservation.getTotalAmount();
                if (amount == null || expectedAmount == null
                        || amount.compareTo(expectedAmount) != 0) {
                    return "redirect:" + backTarget + "?error=Payment+amount+does+not+match+the+reservation+charge";
                }
                BigDecimal safeAmount = expectedAmount;
                boolean overstayPayment = reservation.getStatus() == Reservation.ReservationStatus.OVERSTAY_PAYMENT_DUE;
                if (overstayPayment && paymentService.hasSettledOverstayPayment(reservationId, safeAmount)) {
                    return "redirect:" + backTarget + "?error=The+overstay+charge+has+already+been+paid";
                }

                payment = paymentService.processReservationPayment(user, reservationId, safeAmount, paymentMethod, cardNumber);

                if (payment.getStatus() == Payment.PaymentStatus.PAID) {
                    if (overstayPayment) {
                        payment = paymentService.markOverstayPayment(payment);
                    }
                    try {
                        reservationService.confirmReservation(reservationId);
                    } catch (Exception completionFailure) {
                        log.error("Payment succeeded but reservation completion failed for reservation={}",
                                reservationId, completionFailure);
                        return "redirect:" + backTarget
                                + "?error=Payment+received+but+reservation+completion+needs+staff+assistance";
                    }
                    if (notificationService != null && user != null) {
                        try {
                            String bookingRef = (reservation.getReservationCode() != null && !reservation.getReservationCode().isEmpty())
                                    ? reservation.getReservationCode() : String.valueOf(reservation.getId());
                            // [VIVA - Observer Pattern Dispatch]: Publishes event via Singleton Subject; decouples notifications & audit logs from payment execution.
                            ParkingEventSubject.getInstance().publish(new ParkingEvent(
                                    user.getId(),
                                    "Payment Received",
                                    "Payment of LKR " + safeAmount + " for reservation #" + bookingRef + " was completed successfully (Txn: " + payment.getTransactionCode() + ").",
                                    Notification.NotificationType.PAYMENT_SUCCESSFUL,
                                    reservationId
                            ));
                        } catch (Exception ignored) {}
                    }
                }
            } else {
                payment = paymentService.processGeneralPayment(user, amount, paymentMethod, cardNumber);
                if (payment.getStatus() == Payment.PaymentStatus.PAID && notificationService != null && user != null) {
                    try {
                        ParkingEventSubject.getInstance().publish(new ParkingEvent(
                                user.getId(),
                                "Payment Received",
                                "Payment of LKR " + amount + " has been received successfully (Txn: " + payment.getTransactionCode() + ").",
                                Notification.NotificationType.PAYMENT_SUCCESSFUL,
                                payment.getId()
                        ));
                    } catch (Exception ignored) {}
                }
            }

            if (payment.getStatus() == Payment.PaymentStatus.FAILED) {
                return "redirect:" + backTarget + "?error=Payment+failed:+" + urlSafe(payment.getFailureReason());
            }
            if (payment.getStatus() == Payment.PaymentStatus.PENDING) {
                return "redirect:" + backTarget + "?success=Booking+confirmed.+Pay+" + urlSafe(payment.getMaskedCardNumber()) + "+at+entry.";
            }
            return "redirect:" + backTarget + "?success=Payment+" + payment.getTransactionCode() + "+processed+successfully!";
        } catch (Exception e) {
            // A DataAccessException carries a raw driver/SQL message that is
            // neither meaningful nor safe to show a customer - log the full
            // detail server-side and surface a generic, friendly message
            // instead. Any other exception here already carries a message
            // written to be customer-facing, so it passes through as-is.
            String userMessage;
            if (e instanceof DataAccessException) {
                log.error("Payment processing failed for user={} reservationId={}", user.getId(), reservationId, e);
                userMessage = "We couldn't process your payment due to a system error. Please try again in a moment.";
            } else {
                userMessage = e.getMessage();
            }
            return "redirect:" + backTarget + "?error=" + urlSafe(userMessage);
        }
    }

    // --- Admin Payment Ledger & Financial Audit ---

    @GetMapping("/admin/payments")
    public String adminPaymentLedger(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate,
                                     @RequestParam(required = false) String transactionCode,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";
        if (!securityUtil.hasRole("ADMIN") && (user.getRole() == null || !"ADMIN".equalsIgnoreCase(user.getRole().getName()))) {
            return "redirect:/dashboard";
        }

        List<Payment> allPayments;
        try {
            allPayments = paymentService.getAllPayments();
            if (allPayments == null) allPayments = new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to load payments for admin ledger", e);
            allPayments = new ArrayList<>();
            model.addAttribute("error", "Unable to load some payment transactions: " + e.getMessage());
        }

        // Summary KPI Metrics (Both PAID and COMPLETED count as settled revenue)
        BigDecimal totalRevenue = allPayments.stream()
                .filter(p -> p != null && (p.getStatus() == Payment.PaymentStatus.PAID || p.getStatus() == Payment.PaymentStatus.COMPLETED))
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long completedCount = allPayments.stream()
                .filter(p -> p != null && (p.getStatus() == Payment.PaymentStatus.PAID || p.getStatus() == Payment.PaymentStatus.COMPLETED))
                .count();

        long pendingCount = allPayments.stream()
                .filter(p -> p != null && p.getStatus() == Payment.PaymentStatus.PENDING)
                .count();

        long refundedCount = allPayments.stream()
                .filter(p -> p != null && p.getStatus() == Payment.PaymentStatus.REFUNDED)
                .count();

        long failedCount = allPayments.stream()
                .filter(p -> p != null && p.getStatus() == Payment.PaymentStatus.FAILED)
                .count();

        Map<String, BigDecimal> revenueByMethod = new HashMap<>();
        for (Payment.PaymentMethod m : Payment.PaymentMethod.values()) {
            revenueByMethod.put(m.name(), BigDecimal.ZERO);
        }
        for (Payment p : allPayments) {
            if (p != null && (p.getStatus() == Payment.PaymentStatus.PAID || p.getStatus() == Payment.PaymentStatus.COMPLETED) && p.getPaymentMethod() != null) {
                String methodName = p.getPaymentMethod().name();
                BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
                revenueByMethod.put(methodName, revenueByMethod.getOrDefault(methodName, BigDecimal.ZERO).add(amt));
            }
        }

        // Filtering
        List<Payment> filtered = allPayments.stream().filter(p -> {
            if (p == null) return false;
            if (status != null && !status.trim().isEmpty()) {
                if (p.getStatus() == null) return false;
                String stName = p.getStatus().name();
                String filterSt = status.trim();
                // Treat PAID and COMPLETED as equivalent when filtering for completed payments
                boolean statusMatches = stName.equalsIgnoreCase(filterSt) ||
                        ("PAID".equalsIgnoreCase(filterSt) && "COMPLETED".equalsIgnoreCase(stName)) ||
                        ("COMPLETED".equalsIgnoreCase(filterSt) && "PAID".equalsIgnoreCase(stName));
                if (!statusMatches) return false;
            }
            if (transactionCode != null && !transactionCode.trim().isEmpty()) {
                if (p.getTransactionCode() == null || !p.getTransactionCode().toLowerCase().contains(transactionCode.trim().toLowerCase())) {
                    return false;
                }
            }
            if (startDate != null && !startDate.trim().isEmpty()) {
                try {
                    LocalDate start = LocalDate.parse(startDate.trim());
                    if (p.getCreatedAt() == null || p.getCreatedAt().toLocalDate().isBefore(start)) return false;
                } catch (Exception ignored) {}
            }
            if (endDate != null && !endDate.trim().isEmpty()) {
                try {
                    LocalDate end = LocalDate.parse(endDate.trim());
                    if (p.getCreatedAt() == null || p.getCreatedAt().toLocalDate().isAfter(end)) return false;
                } catch (Exception ignored) {}
            }
            return true;
        }).collect(Collectors.toList());

        int totalTransactions = filtered.size();
        int safeSize = Math.max(1, size);
        int totalPages = (int) Math.ceil((double) totalTransactions / safeSize);
        int safePage = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
        int fromIndex = Math.min(safePage * safeSize, totalTransactions);
        int toIndex = Math.min(fromIndex + safeSize, totalTransactions);
        List<Payment> paged = filtered.subList(fromIndex, toIndex);

        model.addAttribute("payments", paged);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("refundedCount", refundedCount);
        model.addAttribute("failedCount", failedCount);
        model.addAttribute("revenueByMethod", revenueByMethod);
        model.addAttribute("allStatuses", Payment.PaymentStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("transactionCode", transactionCode);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);

        return "admin/payments";
    }

    // --- Admin Refund Action ---

    @PostMapping("/admin/payments/refund/{id}")
    public String refundPayment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = securityUtil.getCurrentUser();
        if (user == null || !securityUtil.hasRole("ADMIN")) {
            return "redirect:/login";
        }

        try {
            paymentService.refundPaymentById(id, "Refunded by Administrator");
            redirectAttributes.addFlashAttribute("success", "Payment #" + id + " refunded successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to refund payment: " + e.getMessage());
        }
        return "redirect:/admin/payments";
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
