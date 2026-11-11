package com.example.parkngo.reservation;

import com.example.parkngo.discount.*;
import com.example.parkngo.facility.*;
import com.example.parkngo.space.*;
import com.example.parkngo.subscription.*;
import com.example.parkngo.user.User;
import com.example.parkngo.vehicle.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationMembershipPricingTest {
    @Test
    void paymentPreviewIncludesConfiguredDiscountForEligibleFacilityBeforeBaySelection() throws Exception {
        var vehicles = mock(VehicleService.class);
        var facilities = mock(ParkingFacilityService.class);
        var spaces = mock(ParkingSpaceService.class);
        var services = mock(FacilityServiceRepository.class);
        var discounts = mock(DiscountService.class);
        var subscriptions = mock(SubscriptionService.class);
        var controller = new ReservationController(null, vehicles, facilities, spaces, services, discounts,
                null, null, null, subscriptions, null);
        User customer = new User(); customer.setId(4L);
        ParkingFacility eligible = new ParkingFacility(); eligible.setId(7L);
        ParkingFacility other = new ParkingFacility(); other.setId(8L);
        com.example.parkngo.discount.Package pkg = new com.example.parkngo.discount.Package();
        pkg.setName("Commuter"); pkg.setAllFacilities(false); pkg.setFacilities(Set.of(eligible));
        pkg.setDeleted(true); pkg.setActive(false); // Existing buyers retain benefits after catalog removal.
        MembershipBenefit benefit = new MembershipBenefit(); benefit.setType(MembershipBenefit.BenefitType.PARKING_DISCOUNT);
        benefit.setValueAmount(BigDecimal.valueOf(25)); pkg.setBenefits(Set.of(benefit));
        Subscription pass = new Subscription(); pass.setMembershipPackage(pkg);
        when(facilities.getAllFacilities()).thenReturn(List.of(eligible, other));
        when(subscriptions.getParkingMemberships(4L)).thenReturn(List.of(pass));
        var model = new ExtendedModelMap();

        ReflectionTestUtils.invokeMethod(controller, "populateFormModel", model, customer, new Reservation(), null, null);

        Map<?, ?> pricing = (Map<?, ?>) model.get("membershipPricing");
        assertEquals(Map.of("percent", BigDecimal.valueOf(25), "name", "Commuter"), pricing.get(7L));
        assertFalse(pricing.containsKey(8L));
        verify(subscriptions, never()).subscribe(any(), any());
    }
}
