package com.example.parkngo.loyalty;

import jakarta.persistence.*;

/**
 * LoyaltyStatus.java
 * "Loyalty-lite": an automatic tier unlocked purely by reservation history -
 * no purchase, no billing. customerId is used directly as the primary key
 * (one row per customer, created on first read/write) rather than a
 * separate generated id, since this table has no independent identity of
 * its own beyond "which customer".
 */
@Entity
@Table(name = "LoyaltyStatus")
public class LoyaltyStatus {

    @Id
    @Column(name = "CustomerID")
    private Long customerId;

    @Column(name = "CompletedReservationCount", nullable = false)
    private int completedReservationCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "Tier", nullable = false)
    private LoyaltyTier tier = LoyaltyTier.STANDARD;

    public enum LoyaltyTier {
        STANDARD, GOLD
    }

    public LoyaltyStatus() {
    }

    public LoyaltyStatus(Long customerId) {
        this.customerId = customerId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public int getCompletedReservationCount() {
        return completedReservationCount;
    }

    public void setCompletedReservationCount(int completedReservationCount) {
        this.completedReservationCount = completedReservationCount;
    }

    public LoyaltyTier getTier() {
        return tier;
    }

    public void setTier(LoyaltyTier tier) {
        this.tier = tier;
    }
}
