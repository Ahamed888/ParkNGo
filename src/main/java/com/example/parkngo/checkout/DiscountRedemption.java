package com.example.parkngo.checkout;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * DiscountRedemption.java
 * One row per attempt to redeem a discount code against a checkout quote.
 * Created as HELD the moment a quote places the hold (see
 * CheckoutService.tryHoldDiscount) so that a second, concurrent hold attempt
 * by the SAME customer for a one-per-customer code is correctly blocked
 * before either is committed - counting confirmed redemptions alone would
 * be too late, since both attempts could be sitting in an uncommitted HELD
 * state at once. Flips to CONFIRMED when the owning quote is committed, or
 * is deleted outright if the quote's hold expires unconsumed. A REVOKED row
 * is kept (not deleted) when a committed redemption is later rolled back,
 * so the record of "this customer did redeem this code once" survives for
 * auditing even though the usage slot itself was given back.
 */
@Entity
@Table(name = "DiscountRedemption")
public class DiscountRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RedemptionID")
    private Long id;

    @Column(name = "DiscountID", nullable = false)
    private Long discountId;

    @Column(name = "CustomerID", nullable = false)
    private Long customerId;

    @Column(name = "QuoteID", nullable = false)
    private Long quoteId;

    // Set only once the owning quote is committed (status -> CONFIRMED).
    @Column(name = "OrderReferenceID")
    private Long orderReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private RedemptionStatus status = RedemptionStatus.HELD;

    @Column(name = "RedeemedAt", nullable = false, updatable = false)
    private LocalDateTime redeemedAt = LocalDateTime.now();

    public enum RedemptionStatus {
        HELD, CONFIRMED, REVOKED
    }

    public DiscountRedemption() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDiscountId() {
        return discountId;
    }

    public void setDiscountId(Long discountId) {
        this.discountId = discountId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getQuoteId() {
        return quoteId;
    }

    public void setQuoteId(Long quoteId) {
        this.quoteId = quoteId;
    }

    public Long getOrderReferenceId() {
        return orderReferenceId;
    }

    public void setOrderReferenceId(Long orderReferenceId) {
        this.orderReferenceId = orderReferenceId;
    }

    public RedemptionStatus getStatus() {
        return status;
    }

    public void setStatus(RedemptionStatus status) {
        this.status = status;
    }

    public LocalDateTime getRedeemedAt() {
        return redeemedAt;
    }

    public void setRedeemedAt(LocalDateTime redeemedAt) {
        this.redeemedAt = redeemedAt;
    }
}
