package com.example.parkngo.discount;

import com.example.parkngo.facility.FacilityService;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** Configurable benefit belonging to a membership package. */
@Entity
@Table(name = "MembershipBenefit")
public class MembershipBenefit {
    // Retired types remain readable for previously purchased memberships.
    public enum BenefitType {
        PARKING_DISCOUNT, NTH_PARKING_FREE, FREE_OR_DISCOUNTED_SERVICE,
        EV_CHARGING_ALLOWANCE, PARTNER_REWARD, PARKING_ALLOWANCE;

        public boolean isConfigurable() {
            return this == PARKING_DISCOUNT || this == NTH_PARKING_FREE || this == FREE_OR_DISCOUNTED_SERVICE;
        }
    }
    public enum Frequency { DAILY, MONTHLY, PER_VISIT, UNLIMITED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BenefitID") private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "PackageID", nullable = false)
    private Package membershipPackage;
    @Enumerated(EnumType.STRING) @Column(name = "BenefitType", nullable = false)
    private BenefitType type;
    @Column(name = "ValueAmount", precision = 12, scale = 2) private BigDecimal valueAmount;
    @Column(name = "QualifyingCount") private Integer qualifyingCount;
    @Column(name = "AllowanceMinutes") private Integer allowanceMinutes;
    @Column(name = "RewardDescription", length = 500) private String rewardDescription;
    @Enumerated(EnumType.STRING) @Column(name = "Frequency", nullable = false)
    private Frequency frequency = Frequency.UNLIMITED;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "ServiceID")
    private FacilityService facilityService;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Package getMembershipPackage() { return membershipPackage; } public void setMembershipPackage(Package membershipPackage) { this.membershipPackage = membershipPackage; }
    public BenefitType getType() { return type; } public void setType(BenefitType type) { this.type = type; }
    public BigDecimal getValueAmount() { return valueAmount; } public void setValueAmount(BigDecimal valueAmount) { this.valueAmount = valueAmount; }
    public Integer getQualifyingCount() { return qualifyingCount; } public void setQualifyingCount(Integer qualifyingCount) { this.qualifyingCount = qualifyingCount; }
    public Integer getAllowanceMinutes() { return allowanceMinutes; } public void setAllowanceMinutes(Integer allowanceMinutes) { this.allowanceMinutes = allowanceMinutes; }
    public String getRewardDescription() { return rewardDescription; } public void setRewardDescription(String rewardDescription) { this.rewardDescription = rewardDescription; }
    public Frequency getFrequency() { return frequency; } public void setFrequency(Frequency frequency) { this.frequency = frequency; }
    public FacilityService getFacilityService() { return facilityService; } public void setFacilityService(FacilityService facilityService) { this.facilityService = facilityService; }

    @Transient public String getDisplayDescription() {
        return switch (type) {
            case PARKING_DISCOUNT -> valueAmount + "% off eligible parking";
            case NTH_PARKING_FREE -> "Free parking every " + qualifyingCount + " qualifying visits";
            case EV_CHARGING_ALLOWANCE -> allowanceMinutes + " minutes charging " + frequency.name().toLowerCase();
            case PARKING_ALLOWANCE -> allowanceMinutes + " free parking minutes " + frequency.name().toLowerCase();
            case FREE_OR_DISCOUNTED_SERVICE -> (valueAmount != null && valueAmount.signum() > 0 ? valueAmount + "% off " : "Free ") + (facilityService != null ? facilityService.getName() : "service");
            case PARTNER_REWARD -> rewardDescription;
        };
    }
}
