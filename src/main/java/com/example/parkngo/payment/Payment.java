package com.example.parkngo.payment;

import com.example.parkngo.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment.java
 * Centralized transaction ledger for reservation checkouts, subscription
 * purchases, and ad-hoc/manual payments. No real payment gateway is
 * integrated (see System Limitations) - this simulates a gateway response
 * while keeping the same shape (masked card, transaction code, status) a
 * real integration would need.
 *
 * Reservation / Subscription are referenced by plain id (not a JPA
 * relationship) so this module has no compile-time dependency on the
 * reservation or subscription packages beyond Subscription itself, and so
 * the payment ledger keeps working even if a referenced reservation is
 * later removed.
 */
@Entity
@Table(name = "Payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PaymentID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = true)
    private User user;

    @Column(name = "ReservationID")
    private Long reservationId;

    @Column(name = "SubscriptionID")
    private Long subscriptionId;

    @NotNull
    @Column(name = "Amount", nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "PaymentMethod", nullable = false)
    private PaymentMethod paymentMethod;

    // VISA / MASTERCARD / AMEX / DISCOVER / UNKNOWN / N/A
    @Column(name = "CardBrand")
    private String cardBrand;

    // e.g. "•••• 4242" - the raw card number is never persisted
    @Column(name = "MaskedCardNumber")
    private String maskedCardNumber;

    @NotNull
    @Column(name = "TransactionID", nullable = false, unique = true)
    private String transactionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "PaymentType", nullable = true)
    private PaymentType paymentType;

    @Column(name = "FailureReason")
    private String failureReason;

    @Column(name = "Remarks")
    private String remarks;

    @Column(name = "PaymentDate", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum PaymentMethod {
        // PAYPAL and APPLE_PAY are no longer offered or processed; they are kept
        // only so any historical Payment rows still load without errors.
        CREDIT_CARD, DEBIT_CARD, PAYPAL, APPLE_PAY, WALLET, DIGITAL_WALLET, NET_BANKING, ONLINE_BANKING, COUPON, CASH, CASH_ON_ENTRY
    }

    public enum PaymentStatus {
        PENDING, PAID, COMPLETED, FAILED, REFUNDED;

        /**
         * COMPLETED is retained for payment records created by earlier
         * versions of ParkNGo. New successful payments use PAID.
         */
        public boolean isSettled() {
            return this == PAID || this == COMPLETED;
        }
    }

    public enum PaymentType {
        RESERVATION, SUBSCRIPTION, GENERAL
    }

    public Payment() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    // Convenience alias used by admin templates as a fallback when the
    // related User cannot be loaded.
    public Long getCustomerId() {
        return user != null ? user.getId() : null;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    public String getMaskedCardNumber() {
        return maskedCardNumber;
    }

    public void setMaskedCardNumber(String maskedCardNumber) {
        this.maskedCardNumber = maskedCardNumber;
    }

    public String getTransactionCode() {
        return transactionCode;
    }

    public void setTransactionCode(String transactionCode) {
        this.transactionCode = transactionCode;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
