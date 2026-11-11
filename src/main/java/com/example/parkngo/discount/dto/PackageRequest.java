package com.example.parkngo.discount.dto;

import com.example.parkngo.discount.Package;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * PackageRequest.java
 * The web-facing shape of the admin package create/edit form, kept separate
 * from the {@link Package} JPA entity. Binding a Thymeleaf form straight to
 * an @Entity is a common source of "overposting" bugs (a submitted field the
 * form never intended to expose silently overwrites a column) and it also
 * means the entity's persistence-oriented defaults (e.g. price = ZERO)
 * leak into a brand new, blank create form. A request DTO fixes both: it
 * only carries the fields the form actually edits, has no default values of
 * its own (so a new package form renders genuinely blank), and Bean
 * Validation annotations here are enforced with @Valid at the controller
 * boundary, before the service layer ever sees the data.
 */
public class PackageRequest {

    private Long id;

    @NotBlank(message = "Package name is required")
    @Size(max = 255, message = "Package name must be 255 characters or fewer")
    private String name;

    private Package.PackageTier tier;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    @NotNull(message = "Discount percentage is required")
    @DecimalMin(value = "0.0", message = "Discount percentage must be zero or greater")
    @DecimalMax(value = "100.0", message = "Discount percentage cannot exceed 100")
    private BigDecimal discountPercentage;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 day")
    private Integer durationDays;

    // Optional - left blank means "unlimited within the duration window".
    // @Min alone (without @NotNull) lets null pass through untouched while
    // still rejecting a submitted 0 or negative value.
    @Min(value = 1, message = "Visit count must be at least 1 if the pass is visit-limited")
    private Integer visitCount;

    @Size(max = 2000, message = "Description must be 2000 characters or fewer")
    private String description;

    @Size(max = 500, message = "Included services must be 500 characters or fewer")
    private String includedServices;

    private boolean allFacilities = true;

    private List<Long> facilityIds = new ArrayList<>();

    private List<Long> serviceIds = new ArrayList<>();

    private List<MembershipBenefitRequest> benefits = new ArrayList<>();

    // Defaults to true so a brand new package is active/listed unless the
    // admin explicitly unchecks it - unlike price/discount/duration, there is
    // no ambiguity risk here since the checkbox state is always visible.
    private boolean active = true;

    public PackageRequest() {
    }

    /** Builds the edit-form DTO from an existing, persisted Package. */
    public static PackageRequest fromEntity(Package pkg) {
        PackageRequest request = new PackageRequest();
        request.id = pkg.getId();
        request.name = pkg.getName();
        request.tier = pkg.getTier();
        request.price = pkg.getPrice();
        request.discountPercentage = pkg.getDiscountPercentage();
        request.durationDays = pkg.getDurationDays();
        request.visitCount = pkg.getVisitCount();
        request.description = pkg.getDescription();
        request.includedServices = pkg.getIncludedServices();
        request.allFacilities = pkg.isAllFacilities();
        request.facilityIds = pkg.getFacilities().stream().map(facility -> facility.getId()).toList();
        request.serviceIds = pkg.getIncludedServiceItems().stream().map(service -> service.getId()).toList();
        request.benefits = pkg.getBenefits().stream().map(benefit -> {
            MembershipBenefitRequest item = new MembershipBenefitRequest();
            item.setType(benefit.getType()); item.setValueAmount(benefit.getValueAmount());
            item.setQualifyingCount(benefit.getQualifyingCount()); item.setAllowanceMinutes(benefit.getAllowanceMinutes());
            item.setRewardDescription(benefit.getRewardDescription()); item.setFrequency(benefit.getFrequency());
            item.setServiceId(benefit.getFacilityService() == null ? null : benefit.getFacilityService().getId()); return item;
        }).toList();
        request.active = pkg.isActive();
        return request;
    }

    /** Copies this request's editable fields onto a managed/new Package entity. */
    public void applyTo(Package target) {
        target.setName(name);
        // Tier is retained for legacy records but is no longer editable in the package UI.
        if (tier != null) target.setTier(tier);
        target.setPrice(price);
        target.setDiscountPercentage(discountPercentage);
        target.setDurationDays(durationDays);
        target.setVisitCount(visitCount);
        target.setDescription(description);
        target.setIncludedServices(includedServices);
        target.setActive(active);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Package.PackageTier getTier() {
        return tier;
    }

    public void setTier(Package.PackageTier tier) {
        this.tier = tier;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(BigDecimal discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }

    public Integer getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(Integer visitCount) {
        this.visitCount = visitCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIncludedServices() {
        return includedServices;
    }

    public void setIncludedServices(String includedServices) {
        this.includedServices = includedServices;
    }

    public boolean isAllFacilities() {
        return allFacilities;
    }

    public void setAllFacilities(boolean allFacilities) {
        this.allFacilities = allFacilities;
    }

    public List<Long> getFacilityIds() {
        return facilityIds;
    }

    public void setFacilityIds(List<Long> facilityIds) {
        this.facilityIds = facilityIds != null ? facilityIds : new ArrayList<>();
    }

    public List<Long> getServiceIds() {
        return serviceIds;
    }

    public void setServiceIds(List<Long> serviceIds) {
        this.serviceIds = serviceIds != null ? serviceIds : new ArrayList<>();
    }
    public List<MembershipBenefitRequest> getBenefits() { return benefits; }
    public void setBenefits(List<MembershipBenefitRequest> benefits) { this.benefits = benefits != null ? benefits : new ArrayList<>(); }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
