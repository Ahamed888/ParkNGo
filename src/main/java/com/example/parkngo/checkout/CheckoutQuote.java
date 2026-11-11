package com.example.parkngo.checkout;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CheckoutQuote.java
 * Binds what a preview showed a customer to what commit is later allowed to
 * apply. Preview does not just report a price - placing a quote is the
 * moment a package visit or a discount slot is actually, provisionally held
 * (see CheckoutService.previewCheckout). Commit finalizes that exact hold
 * rather than recomputing anything, which is what makes commit both
 * race-safe (nothing is decided again at commit time) and idempotent (the
 * same quote can only ever be consumed by one order).
 *
 * orderReferenceId/consumedByOrderReferenceId are deliberately plain Longs,
 * not JPA relations to any "order" entity - this module has no such entity
 * of its own, and today's only caller (Subscription purchase) and a future
 * caller (Reservation checkout, owned by a teammate) mean different things
 * by "order". Keeping it opaque avoids a compile-time dependency on either.
 */
@Entity
@Table(name = "CheckoutQuote")
public class CheckoutQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QuoteID")
    private Long id;

    @Column(name = "CustomerID", nullable = false)
    private Long customerId;

    @Column(name = "OriginalAmount", nullable = false)
    private BigDecimal originalAmount;

    @Column(name = "FinalAmount", nullable = false)
    private BigDecimal finalAmount;

    // Set when an active, visit-limited or unlimited Subscription pass was
    // held against this quote (Priority 1 - package beats discount/loyalty).
    @Column(name = "AppliedSubscriptionID")
    private Long appliedSubscriptionId;

    // Set when a discount code was held against this quote (Priority 2).
    @Column(name = "AppliedDiscountID")
    private Long appliedDiscountId;

    @Column(name = "LoyaltyApplied", nullable = false)
    private boolean loyaltyApplied = false;

    @Column(name = "LoyaltyAmount", nullable = false)
    private BigDecimal loyaltyAmount = BigDecimal.ZERO;

    // Set only once commitCheckout succeeds - the id of whatever this
    // module's caller considers "the order" (a Subscription id today).
    @Column(name = "ConsumedByOrderReferenceID")
    private Long consumedByOrderReferenceId;

    // True once rollbackCheckout has reversed a committed quote - guards
    // rollback itself against being applied twice (which would otherwise
    // double-restore a visit or double-decrement a discount's uses_count).
    @Column(name = "RolledBack", nullable = false)
    private boolean rolledBack = false;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "ExpiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private QuoteStatus status = QuoteStatus.OPEN;

    public enum QuoteStatus {
        OPEN, CONSUMED, EXPIRED
    }

    public CheckoutQuote() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(BigDecimal finalAmount) {
        this.finalAmount = finalAmount;
    }

    public Long getAppliedSubscriptionId() {
        return appliedSubscriptionId;
    }

    public void setAppliedSubscriptionId(Long appliedSubscriptionId) {
        this.appliedSubscriptionId = appliedSubscriptionId;
    }

    public Long getAppliedDiscountId() {
        return appliedDiscountId;
    }

    public void setAppliedDiscountId(Long appliedDiscountId) {
        this.appliedDiscountId = appliedDiscountId;
    }

    public boolean isLoyaltyApplied() {
        return loyaltyApplied;
    }

    public void setLoyaltyApplied(boolean loyaltyApplied) {
        this.loyaltyApplied = loyaltyApplied;
    }

    public BigDecimal getLoyaltyAmount() {
        return loyaltyAmount;
    }

    public void setLoyaltyAmount(BigDecimal loyaltyAmount) {
        this.loyaltyAmount = loyaltyAmount;
    }

    public Long getConsumedByOrderReferenceId() {
        return consumedByOrderReferenceId;
    }

    public void setConsumedByOrderReferenceId(Long consumedByOrderReferenceId) {
        this.consumedByOrderReferenceId = consumedByOrderReferenceId;
    }

    public boolean isRolledBack() {
        return rolledBack;
    }

    public void setRolledBack(boolean rolledBack) {
        this.rolledBack = rolledBack;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public void setStatus(QuoteStatus status) {
        this.status = status;
    }
}
