package com.example.parkngo.subscription;

import com.example.parkngo.discount.MembershipBenefit;
import com.example.parkngo.discount.Package;
import com.example.parkngo.facility.ParkingFacility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipBenefitServiceTest {
    @Mock BenefitUsageRepository usageRepository;
    @Mock SubscriptionRepository subscriptionRepository;

    @Test
    void activeMembershipIsUsableOnlyAtAssignedActiveFacility() {
        MembershipBenefitService service = new MembershipBenefitService(usageRepository, subscriptionRepository);
        Package membership = new Package(); membership.setId(1L); membership.setAllFacilities(false);
        ParkingFacility assigned = facility(10L, ParkingFacility.FacilityStatus.ACTIVE);
        ParkingFacility unrelated = facility(11L, ParkingFacility.FacilityStatus.ACTIVE);
        membership.setFacilities(new LinkedHashSet<>(java.util.List.of(assigned)));
        MembershipBenefit benefit = new MembershipBenefit(); benefit.setId(3L); benefit.setMembershipPackage(membership); benefit.setType(MembershipBenefit.BenefitType.PARKING_DISCOUNT);
        Subscription subscription = subscription(membership);
        assertTrue(service.isUsable(subscription, benefit, assigned));
        assertFalse(service.isUsable(subscription, benefit, unrelated));
        assigned.setStatus(ParkingFacility.FacilityStatus.INACTIVE);
        assertFalse(service.isUsable(subscription, benefit, assigned));
    }

    @Test
    void nthParkingProgressIsPersistedAndRewardsOnlyEveryNthVisit() {
        MembershipBenefitService service = new MembershipBenefitService(usageRepository, subscriptionRepository);
        Package membership = new Package(); membership.setId(1L); membership.setAllFacilities(true);
        MembershipBenefit benefit = new MembershipBenefit(); benefit.setId(3L); benefit.setMembershipPackage(membership); benefit.setType(MembershipBenefit.BenefitType.NTH_PARKING_FREE); benefit.setQualifyingCount(2);
        Subscription subscription = subscription(membership); ParkingFacility facility = facility(10L, ParkingFacility.FacilityStatus.ACTIVE);
        when(usageRepository.findBySubscription_IdAndBenefit_IdAndPeriodStart(anyLong(), anyLong(), any())).thenReturn(Optional.empty());
        assertFalse(service.recordQualifyingParking(subscription, benefit, facility));
        ArgumentCaptor<BenefitUsage> first = ArgumentCaptor.forClass(BenefitUsage.class); verify(usageRepository).save(first.capture());
        when(usageRepository.findBySubscription_IdAndBenefit_IdAndPeriodStart(anyLong(), anyLong(), any())).thenReturn(Optional.of(first.getValue()));
        assertTrue(service.recordQualifyingParking(subscription, benefit, facility));
        assertEquals(2, first.getValue().getUnitsUsed());
    }

    private Subscription subscription(Package membership) { Subscription sub = new Subscription(); sub.setId(2L); sub.setMembershipPackage(membership); sub.setStatus(Subscription.SubscriptionStatus.ACTIVE); sub.setStartDate(LocalDate.now()); sub.setEndDate(LocalDate.now().plusDays(10)); return sub; }
    private ParkingFacility facility(Long id, ParkingFacility.FacilityStatus status) { ParkingFacility facility = new ParkingFacility(); facility.setId(id); facility.setStatus(status); return facility; }
}
