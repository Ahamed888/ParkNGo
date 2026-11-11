package com.example.parkngo.discount;

import com.example.parkngo.facility.FacilityService;
import com.example.parkngo.facility.ParkingFacility;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "Package")
public class Package {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PackageID")
    private Long id;

    @NotBlank
    @Column(name = "Name", nullable = false, unique = true)
    private String name;

    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @NotNull
    @Column(name = "Price", nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    @NotNull
    @Column(name = "DiscountPercentage", nullable = false)
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    @NotNull
    @Column(name = "ValidityDays", nullable = false)
    private Integer validityDays = 30;

    // Visit-based cap, independent of the duration window above. NULL means
    // "unlimited within the validity period" (the original, duration-only
    // behavior); a non-null value means the pass is consumed after that many
    // uses regardless of how much of the validity window remains. Kept
    // nullable rather than defaulting to 0 so existing packages created
    // before this field existed continue to behave as unlimited passes.
    @Column(name = "VisitCount")
    private Integer visitCount;

    @Column(name = "IncludedServices")
    private String includedServices;

    @Column(name = "AllFacilities", nullable = false)
    private boolean allFacilities = true;

    @Column(name = "Deleted", nullable = false)
    private boolean deleted;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "PackageFacility",
            joinColumns = @JoinColumn(name = "PackageID"),
            inverseJoinColumns = @JoinColumn(name = "FacilityID"))
    private Set<ParkingFacility> facilities = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "PackageIncludedService",
            joinColumns = @JoinColumn(name = "PackageID"),
            inverseJoinColumns = @JoinColumn(name = "ServiceID"))
    private Set<FacilityService> includedServiceItems = new LinkedHashSet<>();

    @OneToMany(mappedBy = "membershipPackage", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private Set<MembershipBenefit> benefits = new LinkedHashSet<>();

    // Marketing tier shown on the customer-facing catalog (Monthly Commuter /
    // Weekend Pass / Annual Corporate). Nullable - older/plain packages
    // render fine without one.
    @Enumerated(EnumType.STRING)
    @Column(name = "Tier")
    private PackageTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private PackageStatus status = PackageStatus.ACTIVE;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum PackageStatus {
        ACTIVE, INACTIVE
    }

    public enum PackageTier {
        MONTHLY_COMMUTER("Commuter"),
        WEEKEND_PASS("Weekend"),
        ANNUAL_CORPORATE("Business"),
        AIRPORT("Airport"),
        PREMIUM("Premium"),
        STANDARD("General");

        private final String displayName;

        PackageTier(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public Package() {}

    public Package(Long id, String name, String description, BigDecimal price, BigDecimal discountPercentage, Integer validityDays, String includedServices, PackageStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.discountPercentage = discountPercentage;
        this.validityDays = validityDays;
        this.includedServices = includedServices;
        this.status = status;
        this.createdAt = createdAt;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    /** Parking pricing uses configured benefits, falling back to legacy packages. */
    @Transient
    public BigDecimal getParkingDiscountPercentage() {
        return benefits.stream()
                .filter(benefit -> benefit.getType() == MembershipBenefit.BenefitType.PARKING_DISCOUNT)
                .map(MembershipBenefit::getValueAmount).filter(java.util.Objects::nonNull)
                .findFirst().orElse(discountPercentage != null ? discountPercentage : BigDecimal.ZERO);
    }

    public void setDiscountPercentage(BigDecimal discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(Integer validityDays) {
        this.validityDays = validityDays;
    }

    // Alias for validityDays - the admin package form binds to "durationDays".
    // Kept as a thin delegate rather than a second column so existing code
    // (DataInitializer, PackageService, customer catalog) that already reads
    // validityDays keeps working unchanged.
    public Integer getDurationDays() {
        return validityDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.validityDays = durationDays;
    }

    public Integer getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(Integer visitCount) {
        this.visitCount = visitCount;
    }

    /** True when this package caps usage by visit count rather than being unlimited-within-duration. */
    public boolean isVisitLimited() {
        return visitCount != null;
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

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public void setAllFacilities(boolean allFacilities) {
        this.allFacilities = allFacilities;
    }

    public Set<ParkingFacility> getFacilities() {
        return facilities;
    }

    public void setFacilities(Set<ParkingFacility> facilities) {
        this.facilities = facilities != null ? facilities : new LinkedHashSet<>();
    }

    public Set<FacilityService> getIncludedServiceItems() {
        return includedServiceItems;
    }

    public void setIncludedServiceItems(Set<FacilityService> includedServiceItems) {
        this.includedServiceItems = includedServiceItems != null ? includedServiceItems : new LinkedHashSet<>();
    }

    public Set<MembershipBenefit> getBenefits() { return benefits; }
    public void setBenefits(Set<MembershipBenefit> benefits) {
        this.benefits.clear();
        if (benefits != null) {
            benefits.forEach(benefit -> { benefit.setMembershipPackage(this); this.benefits.add(benefit); });
        }
    }

    public boolean isEligibleForFacility(Long facilityId) {
        return allFacilities || facilities.stream().anyMatch(facility -> facilityId != null && facilityId.equals(facility.getId()));
    }

    public PackageTier getTier() {
        return tier;
    }

    public void setTier(PackageTier tier) {
        this.tier = tier;
    }

    public PackageStatus getStatus() {
        return status;
    }

    public void setStatus(PackageStatus status) {
        this.status = status;
    }

    // Alias for status - the admin package form binds a checkbox to "active".
    public boolean isActive() {
        return status == PackageStatus.ACTIVE;
    }

    public void setActive(boolean active) {
        this.status = active ? PackageStatus.ACTIVE : PackageStatus.INACTIVE;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
