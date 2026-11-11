package com.example.parkngo.discount;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Discount")
public class Discount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DiscountID")
    private Long id;

    @NotBlank
    @Column(name = "Code", nullable = false, unique = true)
    private String code;

    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @NotNull
    @Column(name = "DiscountValue", nullable = false)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "DiscountType", nullable = false)
    private DiscountType discountType = DiscountType.PERCENTAGE;

    @NotNull
    @Column(name = "ValidFromDate", nullable = false)
    private LocalDate validFromDate;

    @NotNull
    @Column(name = "ValidToDate", nullable = false)
    private LocalDate validToDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private DiscountStatus status = DiscountStatus.ACTIVE;

    // Global cap across every customer combined. NULL means unlimited.
    @Column(name = "MaxUses")
    private Integer maxUses;

    // Per-customer cap so one customer can't drain a promo meant for many.
    // Defaults to 1 (the common "one redemption per customer" case); NULL
    // means no per-customer limit even if a global one exists.
    @Column(name = "MaxUsesPerCustomer")
    private Integer maxUsesPerCustomer = 1;

    // How many times this code has actually been redeemed (held or
    // confirmed) - maintained by the checkout module's hold/release logic,
    // never edited directly through the admin form.
    @Column(name = "UsesCount", nullable = false)
    private Integer usesCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "AppliesTo", nullable = false)
    private AppliesTo appliesTo = AppliesTo.ALL;

    // Only meaningful when appliesTo = OFF_PEAK_ONLY. Off-peak hours are
    // defined locally here (a simple hour-of-day window) rather than
    // depending on the Facility module's own operating-hours config, since
    // this module has no verified API contract with Facility to call into -
    // see the integration note for how a facility-aware version would defer
    // to that module instead.
    @Column(name = "OffPeakStartHour")
    private Integer offPeakStartHour;

    @Column(name = "OffPeakEndHour")
    private Integer offPeakEndHour;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum DiscountType {
        PERCENTAGE, FIXED_AMOUNT
    }

    public enum DiscountStatus {
        ACTIVE, INACTIVE, EXPIRED
    }

    public enum AppliesTo {
        ALL, OFF_PEAK_ONLY
    }

    public Discount() {}

    public Discount(Long id, String code, String description, BigDecimal discountValue, DiscountType discountType, LocalDate validFromDate, LocalDate validToDate, DiscountStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.code = code;
        this.description = description;
        this.discountValue = discountValue;
        this.discountType = discountType;
        this.validFromDate = validFromDate;
        this.validToDate = validToDate;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    // Alias for code - the admin discount form binds to "couponCode".
    public String getCouponCode() {
        return code;
    }

    public void setCouponCode(String couponCode) {
        this.code = couponCode != null ? couponCode.trim().toUpperCase() : null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    // Alias for discountValue - the admin discount form binds to "value".
    public BigDecimal getValue() {
        return discountValue;
    }

    public void setValue(BigDecimal value) {
        this.discountValue = value;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public LocalDate getValidFromDate() {
        return validFromDate;
    }

    public void setValidFromDate(LocalDate validFromDate) {
        this.validFromDate = validFromDate;
    }

    // Alias for validFromDate - the admin discount form binds to "startDate".
    public LocalDate getStartDate() {
        return validFromDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.validFromDate = startDate;
    }

    public LocalDate getValidToDate() {
        return validToDate;
    }

    public void setValidToDate(LocalDate validToDate) {
        this.validToDate = validToDate;
    }

    // Alias for validToDate - the admin discount form binds to "expiryDate".
    public LocalDate getExpiryDate() {
        return validToDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.validToDate = expiryDate;
    }

    public DiscountStatus getStatus() {
        return status;
    }

    public void setStatus(DiscountStatus status) {
        this.status = status;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }

    public Integer getMaxUsesPerCustomer() {
        return maxUsesPerCustomer;
    }

    public void setMaxUsesPerCustomer(Integer maxUsesPerCustomer) {
        this.maxUsesPerCustomer = maxUsesPerCustomer;
    }

    public Integer getUsesCount() {
        return usesCount;
    }

    public void setUsesCount(Integer usesCount) {
        this.usesCount = usesCount;
    }

    public AppliesTo getAppliesTo() {
        return appliesTo;
    }

    public void setAppliesTo(AppliesTo appliesTo) {
        this.appliesTo = appliesTo;
    }

    public Integer getOffPeakStartHour() {
        return offPeakStartHour;
    }

    public void setOffPeakStartHour(Integer offPeakStartHour) {
        this.offPeakStartHour = offPeakStartHour;
    }

    public Integer getOffPeakEndHour() {
        return offPeakEndHour;
    }

    public void setOffPeakEndHour(Integer offPeakEndHour) {
        this.offPeakEndHour = offPeakEndHour;
    }

    // Alias for status - the admin discount form binds a checkbox to "active".
    public boolean isActive() {
        return status == DiscountStatus.ACTIVE;
    }

    public void setActive(boolean active) {
        this.status = active ? DiscountStatus.ACTIVE : DiscountStatus.INACTIVE;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
