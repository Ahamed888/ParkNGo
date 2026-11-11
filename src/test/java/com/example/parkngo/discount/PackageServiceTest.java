package com.example.parkngo.discount;

import com.example.parkngo.discount.dto.PackageRequest;
import com.example.parkngo.discount.exception.DuplicateResourceException;
import com.example.parkngo.discount.exception.ResourceInUseException;
import com.example.parkngo.discount.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * PackageServiceTest.java
 * Pure Mockito unit tests (no Spring context, no database) for the
 * PackageService business rules: uniqueness-on-save, not-found handling, and
 * the guarded delete. PackageRepository is mocked so these run instantly and
 * only exercise PackageService's own logic - the create/update entry point
 * used by the admin form is savePackage(PackageRequest), so that is what
 * these tests drive rather than the legacy entity-based methods.
 */
@ExtendWith(MockitoExtension.class)
class PackageServiceTest {

    @Mock
    private PackageRepository packageRepository;

    @Mock
    private com.example.parkngo.subscription.SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private PackageService packageService;

    private PackageRequest newPackageRequest() {
        PackageRequest request = new PackageRequest();
        request.setName("Weekend Explorer");
        request.setTier(Package.PackageTier.WEEKEND_PASS);
        request.setPrice(BigDecimal.valueOf(29.99));
        request.setDiscountPercentage(BigDecimal.valueOf(15));
        request.setDurationDays(7);
        request.setDescription("Weekend-only parking pass");
        request.setActive(true);
        return request;
    }

    @BeforeEach
    void setUp() {
        // Individual tests stub only the repository calls they actually need.
    }

    @Test
    void savePackage_create_persistsNewPackageWhenNameIsUnique() {
        PackageRequest request = newPackageRequest();
        when(packageRepository.findByName("Weekend Explorer")).thenReturn(Optional.empty());
        when(packageRepository.save(any(Package.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Package saved = packageService.savePackage(request);

        assertEquals("Weekend Explorer", saved.getName());
        assertEquals(Package.PackageTier.WEEKEND_PASS, saved.getTier());
        assertEquals(0, BigDecimal.valueOf(29.99).compareTo(saved.getPrice()));
        assertEquals(7, saved.getDurationDays());
        assertTrue(saved.isActive());
        verify(packageRepository).save(any(Package.class));
    }

    @Test
    void createdBenefitIsPreservedWhenOpeningAndSavingEditForm() {
        PackageRequest request = newPackageRequest();
        var benefit = new com.example.parkngo.discount.dto.MembershipBenefitRequest();
        benefit.setType(MembershipBenefit.BenefitType.PARKING_DISCOUNT);
        benefit.setValueAmount(new BigDecimal("12.50"));
        request.setBenefits(List.of(benefit));
        when(packageRepository.findByName("Weekend Explorer")).thenReturn(Optional.empty());
        when(packageRepository.save(any(Package.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Package saved = packageService.savePackage(request);
        assertEquals(1, saved.getBenefits().size());
        saved.setId(7L);
        when(packageRepository.findById(7L)).thenReturn(Optional.of(saved));
        PackageRequest edit = PackageRequest.fromEntity(packageService.getPackageById(7L));
        assertEquals(1, edit.getBenefits().size());
        assertEquals(MembershipBenefit.BenefitType.PARKING_DISCOUNT, edit.getBenefits().getFirst().getType());
        assertEquals(new BigDecimal("12.50"), edit.getBenefits().getFirst().getValueAmount());
        Package edited = packageService.savePackage(edit);
        assertEquals(1, edited.getBenefits().size());
        assertEquals(new BigDecimal("12.50"), edited.getBenefits().iterator().next().getValueAmount());
    }

    @Test
    void incompleteBenefitCannotBeSilentlyDropped() {
        PackageRequest request = newPackageRequest();
        var benefit = new com.example.parkngo.discount.dto.MembershipBenefitRequest();
        benefit.setValueAmount(BigDecimal.TEN); request.setBenefits(List.of(benefit));
        when(packageRepository.findByName("Weekend Explorer")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> packageService.savePackage(request));
        verify(packageRepository, never()).save(any());
    }

    @Test
    void savePackage_create_rejectsDuplicateName() {
        PackageRequest request = newPackageRequest();
        Package existing = new Package();
        existing.setId(1L);
        existing.setName("Weekend Explorer");
        when(packageRepository.findByName("Weekend Explorer")).thenReturn(Optional.of(existing));

        assertThrows(DuplicateResourceException.class, () -> packageService.savePackage(request));
        verify(packageRepository, never()).save(any(Package.class));
    }

    @Test
    void savePackage_trimsNameBeforeCheckingAndSaving() {
        PackageRequest request = newPackageRequest();
        request.setName("  Weekend Explorer  ");
        when(packageRepository.findByName("Weekend Explorer")).thenReturn(Optional.empty());
        when(packageRepository.save(any(Package.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Package saved = packageService.savePackage(request);

        assertEquals("Weekend Explorer", saved.getName());
    }

    @Test
    void savePackage_update_appliesChangesToExistingPackage() {
        PackageRequest request = newPackageRequest();
        request.setId(5L);
        request.setName("Weekend Explorer Plus");

        Package existing = new Package();
        existing.setId(5L);
        existing.setName("Weekend Explorer");

        when(packageRepository.findByName("Weekend Explorer Plus")).thenReturn(Optional.empty());
        when(packageRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(packageRepository.save(any(Package.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Package saved = packageService.savePackage(request);

        assertEquals(5L, saved.getId());
        assertEquals("Weekend Explorer Plus", saved.getName());
    }

    @Test
    void savePackage_update_rejectsNameCollisionWithAnotherPackage() {
        PackageRequest request = newPackageRequest();
        request.setId(5L);

        Package other = new Package();
        other.setId(99L);
        other.setName("Weekend Explorer");

        when(packageRepository.findByName("Weekend Explorer")).thenReturn(Optional.of(other));

        assertThrows(DuplicateResourceException.class, () -> packageService.savePackage(request));
        verify(packageRepository, never()).findById(anyLong());
        verify(packageRepository, never()).save(any(Package.class));
    }

    @Test
    void savePackage_update_throwsWhenTargetPackageMissing() {
        PackageRequest request = newPackageRequest();
        request.setId(404L);

        when(packageRepository.findByName("Weekend Explorer")).thenReturn(Optional.empty());
        when(packageRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> packageService.savePackage(request));
    }

    @Test
    void toggleStatus_flipsActivePackageToInactive() {
        Package existing = new Package();
        existing.setId(1L);
        existing.setStatus(Package.PackageStatus.ACTIVE);
        when(packageRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(packageRepository.save(any(Package.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Package toggled = packageService.toggleStatus(1L);

        assertFalse(toggled.isActive());
    }

    @Test
    void toggleStatus_throwsWhenPackageMissing() {
        when(packageRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> packageService.toggleStatus(1L));
    }

    @Test
    void deletePackage_removesPurchasedPackageFromCatalogAndPreservesSubscriptions() {
        Package existing = new Package();
        existing.setId(1L);
        when(packageRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.existsByMembershipPackage_Id(1L)).thenReturn(true);

        packageService.deletePackage(1L);

        assertTrue(existing.isDeleted());
        assertFalse(existing.isActive());
        verify(packageRepository).saveAndFlush(existing);
        verify(packageRepository, never()).delete(any(Package.class));
    }

    @Test
    void deletedPackagesAreHiddenFromAdminAndCustomerCatalogs() {
        Package visible = new Package();
        Package deleted = new Package(); deleted.setDeleted(true);
        when(packageRepository.findAll()).thenReturn(List.of(visible, deleted));
        when(packageRepository.findByStatus(Package.PackageStatus.ACTIVE)).thenReturn(List.of(visible, deleted));
        assertEquals(List.of(visible), packageService.getAllPackages());
        assertEquals(List.of(visible), packageService.getActivePackages());
    }

    @Test
    void deletedPackageCannotBeReopenedOrReactivated() {
        Package deleted = new Package(); deleted.setId(1L); deleted.setDeleted(true);
        when(packageRepository.findById(1L)).thenReturn(Optional.of(deleted));
        assertThrows(ResourceNotFoundException.class, () -> packageService.getPackageById(1L));
        assertThrows(ResourceNotFoundException.class, () -> packageService.toggleStatus(1L));
        verify(packageRepository, never()).save(any());
    }

    @Test
    void removedBenefitTypesCannotBeSaved() {
        var facilities = mock(com.example.parkngo.facility.ParkingFacilityRepository.class);
        var services = mock(com.example.parkngo.facility.FacilityServiceRepository.class);
        PackageService service = new PackageService(packageRepository, subscriptionRepository, facilities, services);
        when(packageRepository.findByName("Weekend Explorer")).thenReturn(Optional.empty());
        when(services.findAllById(List.of())).thenReturn(List.of());
        for (var type : List.of(MembershipBenefit.BenefitType.PARKING_ALLOWANCE,
                MembershipBenefit.BenefitType.EV_CHARGING_ALLOWANCE, MembershipBenefit.BenefitType.PARTNER_REWARD)) {
            PackageRequest request = newPackageRequest();
            var benefit = new com.example.parkngo.discount.dto.MembershipBenefitRequest();
            benefit.setType(type); request.setBenefits(List.of(benefit));
            assertThrows(IllegalArgumentException.class, () -> service.savePackage(request));
        }
        verify(packageRepository, never()).save(any());
    }

    @Test
    void deletePackage_translatesForeignKeyViolationIntoResourceInUse() {
        Package existing = new Package();
        existing.setId(1L);
        when(packageRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.existsByMembershipPackage_Id(1L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("FK violation")).when(packageRepository).delete(existing);

        assertThrows(ResourceInUseException.class, () -> packageService.deletePackage(1L));
    }

    @Test
    void deletePackage_succeedsWhenNotReferenced() {
        Package existing = new Package();
        existing.setId(1L);
        when(packageRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.existsByMembershipPackage_Id(1L)).thenReturn(false);

        assertDoesNotThrow(() -> packageService.deletePackage(1L));
        verify(packageRepository).delete(existing);
        verify(packageRepository).flush();
    }

    @Test
    void createPackage_rejectsNegativePrice() {
        Package pkg = new Package();
        pkg.setName("Test Package");
        pkg.setPrice(BigDecimal.valueOf(-500));
        pkg.setValidityDays(30);

        Exception exception = assertThrows(Exception.class, () -> packageService.createPackage(pkg));
        assertEquals("Price must be greater than 0", exception.getMessage());
    }

    @Test
    void createPackage_rejectsZeroPrice() {
        Package pkg = new Package();
        pkg.setName("Test Package");
        pkg.setPrice(BigDecimal.ZERO);
        pkg.setValidityDays(30);

        Exception exception = assertThrows(Exception.class, () -> packageService.createPackage(pkg));
        assertEquals("Price must be greater than 0", exception.getMessage());
    }

    @Test
    void createPackage_acceptsPositivePrice() throws Exception {
        Package pkg = new Package();
        pkg.setName("Test Package");
        pkg.setPrice(BigDecimal.valueOf(45000));
        pkg.setValidityDays(30);
        
        when(packageRepository.findByName("Test Package")).thenReturn(Optional.empty());
        when(packageRepository.save(any(Package.class))).thenReturn(pkg);

        assertDoesNotThrow(() -> packageService.createPackage(pkg));
        verify(packageRepository).save(pkg);
    }

    @Test
    void saveMembership_assignsMultipleActiveFacilities() {
        com.example.parkngo.facility.ParkingFacilityRepository facilities = mock(com.example.parkngo.facility.ParkingFacilityRepository.class);
        com.example.parkngo.facility.FacilityServiceRepository services = mock(com.example.parkngo.facility.FacilityServiceRepository.class);
        PackageService service = new PackageService(packageRepository, subscriptionRepository, facilities, services);
        PackageRequest request = newPackageRequest(); request.setAllFacilities(false); request.setFacilityIds(List.of(10L, 11L));
        com.example.parkngo.facility.ParkingFacility first = new com.example.parkngo.facility.ParkingFacility(); first.setId(10L);
        com.example.parkngo.facility.ParkingFacility second = new com.example.parkngo.facility.ParkingFacility(); second.setId(11L);
        when(packageRepository.findByName("Weekend Explorer")).thenReturn(Optional.empty());
        when(facilities.findAllById(request.getFacilityIds())).thenReturn(List.of(first, second));
        when(services.findAllById(List.of())).thenReturn(List.of());
        when(packageRepository.save(any(Package.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Package saved = service.savePackage(request);
        assertEquals(2, saved.getFacilities().size());
    }

    @Test
    void saveMembership_rejectsNewInactiveFacility() {
        com.example.parkngo.facility.ParkingFacilityRepository facilities = mock(com.example.parkngo.facility.ParkingFacilityRepository.class);
        com.example.parkngo.facility.FacilityServiceRepository services = mock(com.example.parkngo.facility.FacilityServiceRepository.class);
        PackageService service = new PackageService(packageRepository, subscriptionRepository, facilities, services);
        PackageRequest request = newPackageRequest(); request.setAllFacilities(false); request.setFacilityIds(List.of(10L));
        com.example.parkngo.facility.ParkingFacility inactive = new com.example.parkngo.facility.ParkingFacility(); inactive.setId(10L); inactive.setStatus(com.example.parkngo.facility.ParkingFacility.FacilityStatus.INACTIVE);
        when(packageRepository.findByName("Weekend Explorer")).thenReturn(Optional.empty());
        when(facilities.findAllById(request.getFacilityIds())).thenReturn(List.of(inactive));
        assertThrows(IllegalArgumentException.class, () -> service.savePackage(request));
    }

    @Test
    void saveMembership_rejectsInvalidBenefitConfiguration() {
        com.example.parkngo.facility.ParkingFacilityRepository facilities = mock(com.example.parkngo.facility.ParkingFacilityRepository.class);
        com.example.parkngo.facility.FacilityServiceRepository services = mock(com.example.parkngo.facility.FacilityServiceRepository.class);
        PackageService service = new PackageService(packageRepository, subscriptionRepository, facilities, services);
        PackageRequest request = newPackageRequest();
        com.example.parkngo.discount.dto.MembershipBenefitRequest benefit = new com.example.parkngo.discount.dto.MembershipBenefitRequest();
        benefit.setType(MembershipBenefit.BenefitType.NTH_PARKING_FREE); benefit.setQualifyingCount(0); request.setBenefits(List.of(benefit));
        when(packageRepository.findByName("Weekend Explorer")).thenReturn(Optional.empty());
        when(services.findAllById(List.of())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.savePackage(request));
    }
}
