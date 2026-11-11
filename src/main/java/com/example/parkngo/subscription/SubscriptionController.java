package com.example.parkngo.subscription;

import com.example.parkngo.checkout.CheckoutQuote;
import com.example.parkngo.checkout.CheckoutService;
import com.example.parkngo.discount.Package;
import com.example.parkngo.discount.PackageService;
import com.example.parkngo.payment.Payment;
import com.example.parkngo.payment.PaymentService;
import com.example.parkngo.user.User;
import com.example.parkngo.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Optional;

/**
 * SubscriptionController.java
 * Package checkout (subscribe), the printable digital pass receipt, the "My
 * Passes" portal, and self-service cancellation. Purchase optionally accepts
 * a discount code, which is applied through CheckoutService's
 * hold-then-commit flow so the price
 * shown to the customer is exactly the price actually charged, and a
 * capped code can never be over-redeemed by concurrent purchases.
 */
@Controller
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;
    private final PackageService packageService;
    private final PaymentService paymentService;
    private final CheckoutService checkoutService;
    private final SecurityUtil securityUtil;
    private final MembershipBenefitService membershipBenefitService;

    public SubscriptionController(SubscriptionService subscriptionService,
                                   PackageService packageService,
                                   PaymentService paymentService,
                                   CheckoutService checkoutService,
                                   SecurityUtil securityUtil,
                                   MembershipBenefitService membershipBenefitService) {
        this.subscriptionService = subscriptionService;
        this.packageService = packageService;
        this.paymentService = paymentService;
        this.checkoutService = checkoutService;
        this.securityUtil = securityUtil;
        this.membershipBenefitService = membershipBenefitService;
    }

    // --- Checkout (GET: order summary, POST: purchase) ---

    @GetMapping("/customer/package/subscribe/{id}")
    public String checkout(@PathVariable Long id, Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        try {
            Package pkg = packageService.getPackageById(id);
            if (pkg.getStatus() != Package.PackageStatus.ACTIVE) {
                return "redirect:/customer/packages?error=This+package+is+no+longer+available";
            }

            int validityDays = pkg.getValidityDays() != null && pkg.getValidityDays() > 0 ? pkg.getValidityDays() : 30;
            model.addAttribute("package", pkg);
            model.addAttribute("isCheckout", true);
            model.addAttribute("validityDays", validityDays);
            model.addAttribute("startDate", LocalDate.now());
            model.addAttribute("endDate", LocalDate.now().plusDays(validityDays));
            return "customer/subscription-receipt";
        } catch (Exception e) {
            return "redirect:/customer/packages?error=" + urlSafe(e.getMessage());
        }
    }

    @PostMapping("/customer/package/subscribe/{id}")
    public String purchase(@PathVariable Long id,
                            @RequestParam String paymentMethod,
                            @RequestParam(required = false) String cardNumber,
                            @RequestParam(required = false) String cardholderName,
                            @RequestParam(required = false) String expiryDate,
                            @RequestParam(required = false) String cvc,
                            @RequestParam(required = false) String discountCode,
                            Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        Subscription subscription = null;
        try {
            Package pkg = packageService.getPackageById(id);
            if (!"CREDIT_CARD".equals(paymentMethod)) {
                return "redirect:/customer/packages?error=Card+is+the+only+payment+method+for+packages";
            }
            PaymentService.CardValidationError cardError = paymentService.validateCardFields(cardholderName, cardNumber, expiryDate, cvc);
            if (cardError != null) {
                model.addAttribute("paymentFieldErrors", Map.of(cardError.field(), cardError.message()));
                return showCheckoutError(pkg, "Please correct the highlighted card field.", model);
            }

            // Places a provisional hold on the discount code and returns the exact price that will
            // be charged. The hold is only finalized below once payment
            // succeeds - if payment fails, this quote is simply never
            // committed and its hold releases on its own once it expires.
            CheckoutQuote quote = checkoutService.previewPackagePurchase(
                    user.getId(), pkg.getPrice(), discountCode, LocalDateTime.now());

            subscription = subscriptionService.subscribe(user, pkg);

            Payment payment = paymentService.processSubscriptionPayment(
                    user, subscription.getId(), quote.getFinalAmount(), paymentMethod, cardNumber);

            if (payment.getStatus() != Payment.PaymentStatus.PAID
                    && payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
                subscriptionService.cancelSubscription(subscription.getId(), user.getId());
                return showCheckoutError(pkg, payment.getFailureReason() != null
                        ? "Payment failed: " + payment.getFailureReason()
                        : "Payment was not completed. Your pass was not activated.", model);
            }

            checkoutService.commitCheckout(subscription.getId(), quote.getId());
            subscriptionService.activateAfterPayment(subscription.getId(), user.getId());

            return "redirect:/customer/subscription/receipt/" + subscription.getId() + "?success=Membership+activated!";
        } catch (Exception e) {
            if (subscription != null && subscription.getId() != null) {
                try {
                    subscriptionService.cancelSubscription(subscription.getId(), user.getId());
                } catch (Exception ignored) {
                }
            }
            // A DataAccessException (e.g. a constraint violation) carries a
            // raw driver/SQL message that is neither meaningful nor safe to
            // show a customer - log the full detail server-side and surface
            // a generic, friendly message instead. Any other exception here
            // (checkout/business-rule exceptions) already carries a message
            // written to be customer-facing, so it passes through as-is.
            String userMessage;
            if (e instanceof DataAccessException) {
                log.error("Subscription purchase failed for user={} package={}", user.getId(), id, e);
                userMessage = "We couldn't complete your purchase due to a system error. Please try again in a moment.";
            } else {
                userMessage = e.getMessage();
            }
            return "redirect:/customer/packages?error=" + urlSafe(userMessage);
        }
    }

    private String showCheckoutError(Package pkg, String error, Model model) {
        int validityDays = pkg.getValidityDays() != null && pkg.getValidityDays() > 0 ? pkg.getValidityDays() : 30;
        model.addAttribute("package", pkg);
        model.addAttribute("isCheckout", true);
        model.addAttribute("validityDays", validityDays);
        model.addAttribute("startDate", LocalDate.now());
        model.addAttribute("endDate", LocalDate.now().plusDays(validityDays));
        model.addAttribute("error", error);
        return "customer/subscription-receipt";
    }

    // --- Digital Pass Receipt ---

    @GetMapping("/customer/subscription/receipt/{id}")
    public String receipt(@PathVariable Long id, Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        try {
            Subscription subscription = subscriptionService.getSubscriptionById(id);
            boolean isOwner = subscription.getCustomer() != null
                    && subscription.getCustomer().getId().equals(user.getId());
            if (!isOwner && !securityUtil.hasRole("ADMIN")) {
                return "redirect:/customer/subscriptions?error=You+do+not+own+this+pass";
            }

            List<Payment> payments = paymentService.getPaymentsForSubscription(subscription.getId());
            Optional<Payment> latestPayment = payments.stream()
                    .max(java.util.Comparator.comparing(Payment::getCreatedAt));

            model.addAttribute("subscription", subscription);
            model.addAttribute("isCheckout", false);
            model.addAttribute("amountCharged", latestPayment.map(Payment::getAmount).orElse(null));
            return "customer/subscription-receipt";
        } catch (Exception e) {
            return "redirect:/customer/subscriptions?error=" + urlSafe(e.getMessage());
        }
    }

    // --- "My Passes" Portal ---

    @GetMapping("/customer/subscriptions")
    public String mySubscriptions(Model model) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        List<Subscription> subscriptions = subscriptionService.getCustomerSubscriptions(user.getId());
        model.addAttribute("subscriptions", subscriptions);
        java.util.Map<Long, String> benefitProgress = new java.util.HashMap<>();
        subscriptions.forEach(subscription -> subscription.getMembershipPackage().getBenefits().forEach(benefit -> {
            int remaining = membershipBenefitService.remaining(subscription, benefit);
            String progress = switch (benefit.getType()) {
                case NTH_PARKING_FREE -> membershipBenefitService.getUsage(subscription, benefit) + " / " + benefit.getQualifyingCount() + " qualifying visits";
                case EV_CHARGING_ALLOWANCE, PARKING_ALLOWANCE -> remaining + " minutes remaining";
                case FREE_OR_DISCOUNTED_SERVICE, PARTNER_REWARD -> remaining < 0 ? "Available" : remaining + " redemption remaining";
                default -> "Active";
            };
            benefitProgress.put(benefit.getId(), progress);
        }));
        model.addAttribute("benefitProgress", benefitProgress);
        return "customer/subscriptions";
    }

    @PostMapping("/customer/subscription/cancel/{id}")
    public String cancel(@PathVariable Long id) {
        User user = securityUtil.getCurrentUser();
        if (user == null) return "redirect:/login";

        try {
            subscriptionService.cancelSubscription(id, user.getId());
            return "redirect:/customer/subscriptions?success=Pass+cancelled";
        } catch (Exception e) {
            return "redirect:/customer/subscriptions?error=" + urlSafe(e.getMessage());
        }
    }

    /**
     * Percent-encodes free text for safe embedding in a redirect query
     * string. A raw exception message can contain virtually any character
     * (brackets, backticks, parentheses, newlines from a SQL/Hibernate
     * error) - previously only spaces were replaced, so anything else
     * landed unescaped in the URL and Tomcat rejected the whole redirect
     * with a raw "Invalid character found in the request target" page,
     * hiding the real message from the user instead of showing a clean
     * flash-message banner. Also collapses whitespace/newlines and caps the
     * length so a long, multi-line message can't blow up the URL.
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
