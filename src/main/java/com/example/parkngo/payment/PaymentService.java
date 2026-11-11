package com.example.parkngo.payment;

import com.example.parkngo.discount.DiscountService;
import com.example.parkngo.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;

/**
 * PaymentService.java
 * Simulated payment gateway: validates checkout input, auto-detects the card
 * network from the card number, masks the card number before it is ever
 * persisted, and issues a unique, immutable transaction reference.
 * No real gateway (Stripe/PayPal/etc.) is called - see System Limitations.
 *
 * Deliberately no ledger/history query surface here (no "browse all
 * payments" or "revenue by method" reporting) - payment is a supporting step
 * inside the reservation/subscription checkout, not a standalone feature
 * with its own page, so the only reads this module exposes are the ones the
 * checkout workflow itself needs.
 */
@Service
@Transactional
public class PaymentService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TXN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final PaymentRepository paymentRepository;
    private final DiscountService discountService;

    public PaymentService(PaymentRepository paymentRepository, DiscountService discountService) {
        this.paymentRepository = paymentRepository;
        this.discountService = discountService;
    }

    public Payment processReservationPayment(User user, Long reservationId, BigDecimal amount,
                                              String methodRaw, String cardNumber) throws Exception {
        Payment payment = buildPayment(user, amount, methodRaw, cardNumber, Payment.PaymentType.RESERVATION);
        payment.setReservationId(reservationId);
        return paymentRepository.save(payment);
    }

    public Payment processSubscriptionPayment(User user, Long subscriptionId, BigDecimal amount,
                                               String methodRaw, String cardNumber) throws Exception {
        Payment payment = buildPayment(user, amount, methodRaw, cardNumber, Payment.PaymentType.SUBSCRIPTION);
        payment.setSubscriptionId(subscriptionId);
        return paymentRepository.save(payment);
    }

    public Payment processGeneralPayment(User user, BigDecimal amount, String methodRaw, String cardNumber) throws Exception {
        Payment payment = buildPayment(user, amount, methodRaw, cardNumber, Payment.PaymentType.GENERAL);
        return paymentRepository.save(payment);
    }

    private Payment buildPayment(User user, BigDecimal amount, String methodRaw, String cardNumber,
                                  Payment.PaymentType type) throws Exception {
        if (user == null) {
            throw new Exception("You must be signed in to make a payment");
        }
        if (amount == null || amount.signum() < 0) {
            throw new Exception("Invalid payment amount");
        }

        Payment.PaymentMethod method;
        try {
            method = Payment.PaymentMethod.valueOf(methodRaw);
        } catch (Exception e) {
            throw new Exception("Please select a valid payment method");
        }

        Payment payment = new Payment();
        payment.setUser(user);
        payment.setAmount(amount);
        payment.setPaymentMethod(method);
        payment.setPaymentType(type);
        payment.setTransactionCode(generateTransactionCode());

        switch (method) {
            case CREDIT_CARD:
            case DEBIT_CARD: {
                String digitsOnly = cardNumber == null ? "" : cardNumber.replaceAll("[\\s-]", "");
                if (!digitsOnly.matches("\\d{13,16}")) {
                    payment.setStatus(Payment.PaymentStatus.FAILED);
                    payment.setFailureReason("Card number must be 13-16 digits");
                    payment.setMaskedCardNumber(maskCardNumber(digitsOnly));
                } else if (!isValidLuhn(digitsOnly)) {
                    payment.setStatus(Payment.PaymentStatus.FAILED);
                    payment.setFailureReason("Invalid card number (checksum verification failed)");
                    payment.setMaskedCardNumber(maskCardNumber(digitsOnly));
                } else {
                    payment.setCardBrand(detectCardBrand(digitsOnly));
                    payment.setMaskedCardNumber(maskCardNumber(digitsOnly));
                    payment.setStatus(Payment.PaymentStatus.PAID);
                }
                break;
            }
            case WALLET:
                payment.setCardBrand("N/A");
                payment.setMaskedCardNumber("Mobile Wallet (Genie/FriMi/eZ Cash)");
                payment.setStatus(Payment.PaymentStatus.PAID);
                break;
            case NET_BANKING:
                payment.setCardBrand("N/A");
                payment.setMaskedCardNumber("Online Bank Transfer");
                payment.setStatus(Payment.PaymentStatus.PAID);
                break;
            case COUPON: {
                String code = cardNumber == null ? "" : cardNumber.trim().toUpperCase();
                if (code.isEmpty() || !discountService.isValidDiscount(code)) {
                    payment.setStatus(Payment.PaymentStatus.FAILED);
                    String reason = discountService.getDiscountValidationMessage(code);
                    payment.setFailureReason(reason != null ? reason : "This discount code cannot be applied");
                    payment.setMaskedCardNumber(code.isEmpty() ? "No discount code supplied" : ("Discount code: " + code));
                } else {
                    payment.setCardBrand("N/A");
                    payment.setMaskedCardNumber("Discount code: " + code);
                    payment.setStatus(Payment.PaymentStatus.PAID);
                }
                break;
            }
            case CASH_ON_ENTRY:
                payment.setCardBrand("N/A");
                payment.setMaskedCardNumber("Cash at Entry Gate");
                // Cash isn't actually collected yet - stays PENDING until staff settle it.
                payment.setStatus(Payment.PaymentStatus.PENDING);
                break;
            default:
                payment.setStatus(Payment.PaymentStatus.FAILED);
                payment.setFailureReason("Unsupported payment method");
        }

        return payment;
    }

    public static boolean isValidLuhn(String cardNumber) {
        if (cardNumber == null) return false;
        String clean = cardNumber.replaceAll("[\\s-]", "");
        if (!clean.matches("\\d{13,16}")) return false;

        int sum = 0;
        boolean alternate = false;
        for (int i = clean.length() - 1; i >= 0; i--) {
            int n = clean.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    public static boolean isValidExpiryDate(String expiryDate) {
        if (expiryDate == null) return false;
        String clean = expiryDate.trim().replaceAll("\\s", "");
        if (!clean.matches("^(0[1-9]|1[0-2])\\/?([0-9]{2}|[0-9]{4})$")) return false;

        String[] parts = clean.contains("/") ? clean.split("/") : new String[]{clean.substring(0, 2), clean.substring(2)};
        int month = Integer.parseInt(parts[0]);
        int year = Integer.parseInt(parts[1]);
        if (year < 100) {
            year += 2000;
        }

        try {
            java.time.YearMonth cardYearMonth = java.time.YearMonth.of(year, month);
            java.time.YearMonth currentYearMonth = java.time.YearMonth.now();
            return !cardYearMonth.isBefore(currentYearMonth);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidCvc(String cvc) {
        if (cvc == null) return false;
        return cvc.trim().matches("^[0-9]{3,4}$");
    }

    /** Validates the simulated package card form before a subscription is created. */
    public String validatePackageCard(String cardholderName, String cardNumber, String expiryDate, String cvc) {
        CardValidationError error = validateCardFields(cardholderName, cardNumber, expiryDate, cvc);
        return error == null ? null : error.message();
    }

    public CardValidationError validateCardFields(String cardholderName, String cardNumber, String expiryDate, String cvc) {
        if (cardholderName == null || cardholderName.trim().length() < 2) {
            return new CardValidationError("cardholderName", "Enter the cardholder's full name.");
        }
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("[\\s-]", "");
        if (!digits.matches("[0-9]+")) return new CardValidationError("cardNumber", "Enter a card number using digits only.");
        String brand = detectCardBrand(digits);
        if ("UNKNOWN".equals(brand)) return new CardValidationError("cardNumber", "Enter a supported Visa, Mastercard, American Express, Discover, JCB, or Diners Club card number.");
        int length = digits.length();
        boolean validLength = switch (brand) {
            case "VISA" -> length == 13 || length == 16;
            case "MASTERCARD" -> length == 16;
            case "AMEX" -> length == 15;
            case "DISCOVER", "JCB" -> length == 16;
            case "DINERS" -> length >= 14 && length <= 16;
            default -> false;
        };
        if (!validLength) return new CardValidationError("cardNumber", "Enter a valid " + brand + " card number length (maximum 16 digits).");
        if (!isValidLuhn(digits)) return new CardValidationError("cardNumber", "Check the card number and try again.");
        if (expiryDate == null || !expiryDate.matches("(0[1-9]|1[0-2])/[0-9]{2}")
                || !isValidExpiryDate(expiryDate)) return new CardValidationError("expiryDate", "Enter a valid, unexpired date in MM/YY format.");
        int cvcLength = "AMEX".equals(brand) ? 4 : 3;
        if (cvc == null || !cvc.matches("[0-9]{" + cvcLength + "}")) {
            return new CardValidationError("cvc", "Enter the " + cvcLength + "-digit security code for this card.");
        }
        return null;
    }

    public record CardValidationError(String field, String message) {}

    public String detectCardBrand(String digitsOnly) {
        if (digitsOnly == null || !digitsOnly.matches("[0-9]+")) return "UNKNOWN";
        if (digitsOnly.startsWith("4")) return "VISA";
        if (digitsOnly.matches("5[1-5].*")
                || (digitsOnly.length() >= 6 && inRange(digitsOnly, 6, 222100, 272099))) return "MASTERCARD";
        if (digitsOnly.matches("3[47].*")) return "AMEX";
        if (digitsOnly.startsWith("6011") || digitsOnly.startsWith("65")
                || (digitsOnly.length() >= 3 && inRange(digitsOnly, 3, 644, 649))
                || (digitsOnly.length() >= 6 && inRange(digitsOnly, 6, 622126, 622925))) return "DISCOVER";
        if (digitsOnly.length() >= 4 && inRange(digitsOnly, 4, 3528, 3589)) return "JCB";
        if ((digitsOnly.length() >= 3 && inRange(digitsOnly, 3, 300, 305))
                || digitsOnly.startsWith("36") || digitsOnly.startsWith("38") || digitsOnly.startsWith("39")) return "DINERS";
        return "UNKNOWN";
    }

    private static boolean inRange(String digits, int prefixLength, int min, int max) {
        int prefix = Integer.parseInt(digits.substring(0, prefixLength));
        return prefix >= min && prefix <= max;
    }

    public String maskCardNumber(String digitsOnly) {
        if (digitsOnly == null || digitsOnly.length() < 4) {
            return "•••• ****";
        }
        return "•••• " + digitsOnly.substring(digitsOnly.length() - 4);
    }

    private String generateTransactionCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder("TXN-PARK-");
            for (int i = 0; i < 6; i++) {
                sb.append(TXN_CHARS.charAt(RANDOM.nextInt(TXN_CHARS.length())));
            }
            code = sb.toString();
        } while (paymentRepository.existsByTransactionCode(code));
        return code;
    }

    public List<Payment> getPaymentsForReservation(Long reservationId) {
        return paymentRepository.findByReservationId(reservationId);
    }

    public boolean hasSettledOverstayPayment(Long reservationId, BigDecimal amount) {
        return paymentRepository.findByReservationIdAndRemarks(reservationId, "OVERSTAY_PAYMENT").stream()
                .anyMatch(p -> p.getStatus() != null && p.getStatus().isSettled()
                        && p.getAmount() != null && amount != null && p.getAmount().compareTo(amount) == 0);
    }

    public Payment markOverstayPayment(Payment payment) {
        if (payment == null) return null;
        payment.setRemarks("OVERSTAY_PAYMENT");
        return paymentRepository.save(payment);
    }

    public List<Payment> getPaymentsForSubscription(Long subscriptionId) {
        return paymentRepository.findBySubscriptionId(subscriptionId);
    }

    public boolean hasSuccessfulReservationPayment(Long reservationId) {
        return paymentRepository.findByReservationId(reservationId).stream()
                .anyMatch(p -> p.getStatus() != null && p.getStatus().isSettled());
    }

    public void refundReservationPayments(Long reservationId, String reason) {
        List<Payment> payments = paymentRepository.findByReservationId(reservationId);
        for (Payment p : payments) {
            if (p.getStatus() != null && p.getStatus().isSettled()) {
                p.setStatus(Payment.PaymentStatus.REFUNDED);
                p.setFailureReason(reason != null ? reason : "Reservation cancelled");
                paymentRepository.save(p);
            } else if (p.getStatus() == Payment.PaymentStatus.PENDING) {
                p.setStatus(Payment.PaymentStatus.FAILED);
                p.setFailureReason("Cancelled: Reservation cancelled before gate entry (zero charge)");
                paymentRepository.save(p);
            }
        }
    }

    public BigDecimal getPaidAmountForReservation(Long reservationId) {
        return paymentRepository.findByReservationId(reservationId).stream()
                .filter(p -> p.getStatus() != null && p.getStatus().isSettled())
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getRefundedAmountForReservation(Long reservationId) {
        return paymentRepository.findByReservationId(reservationId).stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.REFUNDED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getNetPaidAmountForReservation(Long reservationId) {
        return getPaidAmountForReservation(reservationId);
    }

    public Payment processPartialRefund(User user, Long reservationId, BigDecimal refundAmount, String reason) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        List<Payment> payments = paymentRepository.findByReservationId(reservationId);
        List<Payment> paidPayments = payments.stream()
                .filter(p -> p.getStatus() != null && p.getStatus().isSettled()
                        && p.getAmount() != null && p.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> (b.getId() != null && a.getId() != null) ? b.getId().compareTo(a.getId()) : 0)
                .toList();

        BigDecimal refundableAmount = paidPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refundAmount.compareTo(refundableAmount) > 0) {
            throw new IllegalArgumentException("Refund amount exceeds the amount paid.");
        }

        Payment primaryPaidPayment = paidPayments.isEmpty() ? null : paidPayments.get(paidPayments.size() - 1);

        Payment.PaymentMethod method = Payment.PaymentMethod.CREDIT_CARD;
        String cardBrand = "CARD";
        String maskedCard = "Card Refund";

        if (primaryPaidPayment != null) {
            method = primaryPaidPayment.getPaymentMethod();
            cardBrand = primaryPaidPayment.getCardBrand();
            maskedCard = primaryPaidPayment.getMaskedCardNumber();
        }

        // Deduct refundAmount across paid payments (LIFO: latest payment refunded first)
        BigDecimal remainingToDeduct = refundAmount;
        for (Payment paidPayment : paidPayments) {
            if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal deduction = paidPayment.getAmount().min(remainingToDeduct);
            paidPayment.setAmount(paidPayment.getAmount().subtract(deduction));
            paymentRepository.save(paidPayment);
            remainingToDeduct = remainingToDeduct.subtract(deduction);
        }

        Payment refund = new Payment();
        refund.setUser(user != null ? user : (primaryPaidPayment != null ? primaryPaidPayment.getUser() : null));
        refund.setReservationId(reservationId);
        refund.setAmount(refundAmount);
        refund.setPaymentMethod(method);
        refund.setPaymentType(Payment.PaymentType.RESERVATION);
        refund.setCardBrand(cardBrand != null ? cardBrand : "CARD");
        refund.setMaskedCardNumber(maskedCard != null ? maskedCard : "Card Refund");
        refund.setTransactionCode("REF-" + generateTransactionCode().replace("TXN-PARK-", ""));
        refund.setStatus(Payment.PaymentStatus.REFUNDED);
        refund.setFailureReason(reason != null ? reason : "Booking modification refund");
        return paymentRepository.save(refund);
    }

    public List<Payment> getPaymentsForUser(Long userId) {
        if (userId == null) return List.of();
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Page<Payment> getPaymentsForUser(Long userId, Pageable pageable) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public BigDecimal getTotalSpentForUser(Long userId) {
        if (userId == null) return BigDecimal.ZERO;
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(p -> p.getStatus() != null && p.getStatus().isSettled())
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAllByOrderByCreatedAtDesc();
    }

    public Page<Payment> getAllPayments(Pageable pageable) {
        return paymentRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Payment refundPaymentById(Long paymentId, String reason) {
        if (paymentId == null) return null;
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment != null && payment.getStatus() != null && payment.getStatus().isSettled()) {
            payment.setStatus(Payment.PaymentStatus.REFUNDED);
            payment.setFailureReason(reason != null ? reason : "Refunded by Administrator");
            return paymentRepository.save(payment);
        }
        return payment;
    }
}
