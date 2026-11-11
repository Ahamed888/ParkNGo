package com.example.parkngo.subscription;
import com.example.parkngo.discount.MembershipBenefit;
import jakarta.persistence.*;
import java.time.LocalDate;

/** Server-side usage ledger; periodStart gives daily/monthly benefits their reset boundary. */
@Entity @Table(name = "MembershipBenefitUsage", uniqueConstraints = @UniqueConstraint(columnNames = {"SubscriptionID", "BenefitID", "PeriodStart"}))
public class BenefitUsage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "UsageID") private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "SubscriptionID", nullable = false) private Subscription subscription;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "BenefitID", nullable = false) private MembershipBenefit benefit;
    @Column(name = "PeriodStart", nullable = false) private LocalDate periodStart;
    @Column(name = "UnitsUsed", nullable = false) private int unitsUsed;
    public Long getId() { return id; } public Subscription getSubscription() { return subscription; } public void setSubscription(Subscription subscription) { this.subscription = subscription; }
    public MembershipBenefit getBenefit() { return benefit; } public void setBenefit(MembershipBenefit benefit) { this.benefit = benefit; }
    public LocalDate getPeriodStart() { return periodStart; } public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public int getUnitsUsed() { return unitsUsed; } public void setUnitsUsed(int unitsUsed) { this.unitsUsed = unitsUsed; }
}
