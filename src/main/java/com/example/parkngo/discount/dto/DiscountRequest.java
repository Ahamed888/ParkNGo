package com.example.parkngo.discount.dto;

import com.example.parkngo.discount.Discount;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DiscountRequest.java
 * The web-facing shape of the admin discount coupon create/edit form - see
 * {@link PackageRequest} for why this is kept separate from the
 * {@link Discount} JPA entity. Cross-field rules that Bean Validation alone
 * can't express cleanly (expiry not before start, percentage value capped at
 * 100) are checked in the controller after these per-field constraints pass,
 * and reported back as field-level errors the same way.
 */
public class DiscountRequest {

    private Long id;

    @NotBlank(message = "Discount code is required")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Only letters, numbers, underscores and hyphens are allowed")
    @Size(max = 50, message = "Discount code must be 50 characters or fewer")
    private String couponCode;

    @NotNull(message = "Discount type is required")
    private Discount.DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Discount value must be greater than 0")
    private BigDecimal value;

    // ISO (yyyy-MM-dd) is required here so the value Thymeleaf prints back
    // into the <input type="date"> on the edit form is in the exact format
    // the browser's date picker accepts. Without this annotation, Spring's
    // default LocalDate formatter uses a locale-based style (e.g. "Sep 17,
    // 2026"), which the browser can't parse - it then silently shows the
    // field as blank even though the underlying value is set correctly.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @NotNull(message = "Expiry date is required")
    private LocalDate expiryDate;

    @Size(max = 2000, message = "Description must be 2000 characters or fewer")
    private String description;

    private boolean active = true;

    // Optional - null means unlimited. @Min alone lets a blank field through.
    @Min(value = 1, message = "Global usage cap must be at least 1 if set")
    private Integer maxUses;

    @Min(value = 1, message = "Per-customer usage cap must be at least 1 if set")
    private Integer maxUsesPerCustomer = 1;

    @NotNull(message = "Applicability is required")
    private Discount.AppliesTo appliesTo = Discount.AppliesTo.ALL;

    @Min(value = 0, message = "Off-peak start hour must be between 0 and 23")
    @Max(value = 23, message = "Off-peak start hour must be between 0 and 23")
    private Integer offPeakStartHour;

    @Min(value = 0, message = "Off-peak end hour must be between 0 and 23")
    @Max(value = 23, message = "Off-peak end hour must be between 0 and 23")
    private Integer offPeakEndHour;

    public DiscountRequest() {
    }

    /** Builds the edit-form DTO from an existing, persisted Discount. */
    public static DiscountRequest fromEntity(Discount discount) {
        DiscountRequest request = new DiscountRequest();
        request.id = discount.getId();
        request.couponCode = discount.getCouponCode();
        request.discountType = discount.getDiscountType();
        request.value = discount.getValue();
        request.startDate = discount.getStartDate();
        request.expiryDate = discount.getExpiryDate();
        request.description = discount.getDescription();
        request.active = discount.isActive();
        request.maxUses = discount.getMaxUses();
        request.maxUsesPerCustomer = discount.getMaxUsesPerCustomer();
        request.appliesTo = discount.getAppliesTo();
        request.offPeakStartHour = discount.getOffPeakStartHour();
        request.offPeakEndHour = discount.getOffPeakEndHour();
        return request;
    }

    /** Copies this request's editable fields onto a managed/new Discount entity. */
    public void applyTo(Discount target) {
        target.setCouponCode(couponCode);
        target.setDiscountType(discountType);
        target.setValue(value);
        target.setStartDate(startDate);
        target.setExpiryDate(expiryDate);
        target.setDescription(description);
        target.setActive(active);
        target.setMaxUses(maxUses);
        target.setMaxUsesPerCustomer(maxUsesPerCustomer);
        target.setAppliesTo(appliesTo);
        target.setOffPeakStartHour(offPeakStartHour);
        target.setOffPeakEndHour(offPeakEndHour);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    // Alias for couponCode to match entity and template references
    public String getCode() {
        return couponCode;
    }

    public void setCode(String code) {
        this.couponCode = code;
    }

    // Alias for value to match entity and template references
    public BigDecimal getDiscountValue() {
        return value;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.value = discountValue;
    }

    // Alias for startDate to match entity and template references
    public LocalDate getValidFromDate() {
        return startDate;
    }

    public void setValidFromDate(LocalDate validFromDate) {
        this.startDate = validFromDate;
    }

    // Alias for expiryDate to match entity and template references
    public LocalDate getValidToDate() {
        return expiryDate;
    }

    public void setValidToDate(LocalDate validToDate) {
        this.expiryDate = validToDate;
    }

    public Discount.DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(Discount.DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public Discount.AppliesTo getAppliesTo() {
        return appliesTo;
    }

    public void setAppliesTo(Discount.AppliesTo appliesTo) {
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
}
