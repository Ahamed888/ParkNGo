package com.example.parkngo.subscription;

import com.example.parkngo.discount.Package;
import com.example.parkngo.user.User;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Subscription.java
 * A customer's purchased membership pass: links a customer to the Package
 * they bought, tracks the active window, and carries the simulated gate
 * access PIN.
 */
@Entity
@Table(name = "Subscription")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SubscriptionID")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "CustomerID", nullable = false)
    private User customer;

    @ManyToOne
    @JoinColumn(name = "PackageID", nullable = false)
    private Package membershipPackage;

    @Column(name = "SubscriptionDate", nullable = false)
    private LocalDate startDate;

    @Column(name = "ExpiryDate", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "GateAccessPin", nullable = false, unique = true)
    private String gateAccessPin;

    // Snapshot pattern: copied from the source Package at the moment of
    // purchase so that an admin editing or discontinuing the Package later
    // never retroactively changes what this customer already bought.
    @Column(name = "PackageNameSnap")
    private String packageNameSnap;

    @Column(name = "PricePaidSnap")
    private java.math.BigDecimal pricePaidSnap;

    // Visit-based cap, snapshotted from the package's visitCount at purchase
    // time. NULL means unlimited-within-duration (matches a duration-only
    // Package). Decremented atomically by the checkout module's hold logic.
    @Column(name = "VisitsRemaining")
    private Integer visitsRemaining;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum SubscriptionStatus {
        PENDING, ACTIVE, EXPIRED, CANCELLED, EXHAUSTED
    }

    public Subscription() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public Package getMembershipPackage() {
        return membershipPackage;
    }

    public void setMembershipPackage(Package membershipPackage) {
        this.membershipPackage = membershipPackage;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public String getGateAccessPin() {
        return gateAccessPin;
    }

    public void setGateAccessPin(String gateAccessPin) {
        this.gateAccessPin = gateAccessPin;
    }

    public String getPackageNameSnap() {
        return packageNameSnap;
    }

    public void setPackageNameSnap(String packageNameSnap) {
        this.packageNameSnap = packageNameSnap;
    }

    public java.math.BigDecimal getPricePaidSnap() {
        return pricePaidSnap;
    }

    public void setPricePaidSnap(java.math.BigDecimal pricePaidSnap) {
        this.pricePaidSnap = pricePaidSnap;
    }

    public Integer getVisitsRemaining() {
        return visitsRemaining;
    }

    public void setVisitsRemaining(Integer visitsRemaining) {
        this.visitsRemaining = visitsRemaining;
    }

    /** True when this pass caps usage by visit count rather than being unlimited-within-duration. */
    public boolean isVisitLimited() {
        return visitsRemaining != null;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Days left until this pass expires (never negative). Computed on the
     * fly rather than stored, so it is always accurate.
     */
    public long getDaysRemaining() {
        if (endDate == null) return 0;
        long days = ChronoUnit.DAYS.between(LocalDate.now(), endDate);
        return Math.max(0, days);
    }
}
