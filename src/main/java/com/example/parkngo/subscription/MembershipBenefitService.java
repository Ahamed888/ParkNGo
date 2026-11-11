package com.example.parkngo.subscription;

import com.example.parkngo.discount.MembershipBenefit;
import com.example.parkngo.facility.ParkingFacility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

/** Authoritative membership benefit eligibility and consumption boundary for future parking/service flows. */
@Service @Transactional
public class MembershipBenefitService {
    private final BenefitUsageRepository usageRepository;
    private final SubscriptionRepository subscriptionRepository;
    public MembershipBenefitService(BenefitUsageRepository usageRepository, SubscriptionRepository subscriptionRepository) { this.usageRepository = usageRepository; this.subscriptionRepository = subscriptionRepository; }

    public boolean isUsable(Subscription subscription, MembershipBenefit benefit, ParkingFacility facility) {
        if (subscription == null || benefit == null || facility == null || subscription.getStatus() != Subscription.SubscriptionStatus.ACTIVE || subscription.getEndDate() == null || subscription.getEndDate().isBefore(LocalDate.now())) return false;
        if (subscription.getMembershipPackage() == null || !subscription.getMembershipPackage().isActive() || !facility.isActive()) return false;
        if (benefit.getMembershipPackage() == null || !benefit.getMembershipPackage().getId().equals(subscription.getMembershipPackage().getId())) return false;
        return subscription.getMembershipPackage().isAllFacilities() || subscription.getMembershipPackage().getFacilities().stream().anyMatch(assigned -> assigned.getId().equals(facility.getId()));
    }

    public int getUsage(Subscription subscription, MembershipBenefit benefit) {
        return usageRepository.findBySubscription_IdAndBenefit_IdAndPeriodStart(subscription.getId(), benefit.getId(), periodStart(subscription, benefit)).map(BenefitUsage::getUnitsUsed).orElse(0);
    }

    /** Consumes configured units only after all membership and facility checks pass. */
    public void consume(Subscription subscription, MembershipBenefit benefit, ParkingFacility facility, int units) {
        if (!isUsable(subscription, benefit, facility)) throw new IllegalStateException("Membership benefit is not usable at this parking complex.");
        if (units < 1) throw new IllegalArgumentException("Benefit usage must be positive.");
        int limit = usageLimit(benefit);
        LocalDate period = periodStart(subscription, benefit);
        BenefitUsage usage = usageRepository.findBySubscription_IdAndBenefit_IdAndPeriodStart(subscription.getId(), benefit.getId(), period).orElseGet(() -> {
            BenefitUsage created = new BenefitUsage(); created.setSubscription(subscription); created.setBenefit(benefit); created.setPeriodStart(period); return created;
        });
        if (limit >= 0 && usage.getUnitsUsed() + units > limit) throw new IllegalStateException("This membership benefit has reached its allowance for the current period.");
        usage.setUnitsUsed(usage.getUnitsUsed() + units); usageRepository.save(usage);
    }
    public int remaining(Subscription subscription, MembershipBenefit benefit) { int limit = usageLimit(benefit); return limit < 0 ? -1 : Math.max(0, limit - getUsage(subscription, benefit)); }
    /** Called by a future completed-parking integration; returns true when this qualifying visit earns the free session. */
    public boolean recordQualifyingParking(Subscription subscription, MembershipBenefit benefit, ParkingFacility facility) {
        if (benefit.getType() != MembershipBenefit.BenefitType.NTH_PARKING_FREE) throw new IllegalArgumentException("Benefit is not visit-based.");
        if (!isUsable(subscription, benefit, facility)) throw new IllegalStateException("Membership benefit is not usable at this parking complex.");
        LocalDate period = subscription.getStartDate(); // membership-wide, not a daily reset
        BenefitUsage usage = usageRepository.findBySubscription_IdAndBenefit_IdAndPeriodStart(subscription.getId(), benefit.getId(), period).orElseGet(() -> {
            BenefitUsage created = new BenefitUsage(); created.setSubscription(subscription); created.setBenefit(benefit); created.setPeriodStart(period); return created;
        });
        usage.setUnitsUsed(usage.getUnitsUsed() + 1); usageRepository.save(usage);
        return usage.getUnitsUsed() % benefit.getQualifyingCount() == 0;
    }
    private int usageLimit(MembershipBenefit benefit) {
        return switch (benefit.getType()) {
            case EV_CHARGING_ALLOWANCE, PARKING_ALLOWANCE -> benefit.getAllowanceMinutes() == null ? 0 : benefit.getAllowanceMinutes();
            case FREE_OR_DISCOUNTED_SERVICE, PARTNER_REWARD -> benefit.getFrequency() == MembershipBenefit.Frequency.UNLIMITED ? -1 : 1;
            default -> -1;
        };
    }
    private LocalDate periodStart(Subscription subscription, MembershipBenefit benefit) { LocalDate today = LocalDate.now(); if (benefit.getType() == MembershipBenefit.BenefitType.NTH_PARKING_FREE) return subscription.getStartDate(); return benefit.getFrequency() == MembershipBenefit.Frequency.MONTHLY ? today.withDayOfMonth(1) : today; }
}
