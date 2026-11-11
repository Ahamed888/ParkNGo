package com.example.parkngo.discount.dto;

import com.example.parkngo.discount.MembershipBenefit;
import java.math.BigDecimal;

public class MembershipBenefitRequest {
    private MembershipBenefit.BenefitType type;
    private BigDecimal valueAmount;
    private Integer qualifyingCount;
    private Integer allowanceMinutes;
    private String rewardDescription;
    private MembershipBenefit.Frequency frequency = MembershipBenefit.Frequency.UNLIMITED;
    private Long serviceId;
    public MembershipBenefit.BenefitType getType() { return type; } public void setType(MembershipBenefit.BenefitType type) { this.type = type; }
    public BigDecimal getValueAmount() { return valueAmount; } public void setValueAmount(BigDecimal valueAmount) { this.valueAmount = valueAmount; }
    public Integer getQualifyingCount() { return qualifyingCount; } public void setQualifyingCount(Integer qualifyingCount) { this.qualifyingCount = qualifyingCount; }
    public Integer getAllowanceMinutes() { return allowanceMinutes; } public void setAllowanceMinutes(Integer allowanceMinutes) { this.allowanceMinutes = allowanceMinutes; }
    public String getRewardDescription() { return rewardDescription; } public void setRewardDescription(String rewardDescription) { this.rewardDescription = rewardDescription; }
    public MembershipBenefit.Frequency getFrequency() { return frequency; } public void setFrequency(MembershipBenefit.Frequency frequency) { this.frequency = frequency; }
    public Long getServiceId() { return serviceId; } public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
}
