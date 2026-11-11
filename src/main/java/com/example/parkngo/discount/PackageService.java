package com.example.parkngo.discount;

import com.example.parkngo.discount.dto.PackageRequest;
import com.example.parkngo.discount.dto.MembershipBenefitRequest;
import com.example.parkngo.discount.exception.DuplicateResourceException;
import com.example.parkngo.discount.exception.ResourceInUseException;
import com.example.parkngo.discount.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.parkngo.subscription.SubscriptionRepository;
import com.example.parkngo.facility.FacilityService;
import com.example.parkngo.facility.FacilityServiceRepository;
import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class PackageService {

    private static final Logger log = LoggerFactory.getLogger(PackageService.class);

    private final PackageRepository packageRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ParkingFacilityRepository facilityRepository;
    private final FacilityServiceRepository facilityServiceRepository;

    public PackageService(PackageRepository packageRepository) {
        this(packageRepository, null, null, null);
    }

    public PackageService(PackageRepository packageRepository, SubscriptionRepository subscriptionRepository) {
        this(packageRepository, subscriptionRepository, null, null);
    }

    @Autowired
    public PackageService(PackageRepository packageRepository,
                          @Autowired(required = false) SubscriptionRepository subscriptionRepository,
                          @Autowired(required = false) ParkingFacilityRepository facilityRepository,
                          @Autowired(required = false) FacilityServiceRepository facilityServiceRepository) {
        this.packageRepository = packageRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.facilityRepository = facilityRepository;
        this.facilityServiceRepository = facilityServiceRepository;
    }

    // --- Web-facing entry point (admin create/edit form) ---

    /**
     * Create-or-update entry point used by the single "/admin/packages/save"
     * form: a request with no id creates a new package, otherwise the
     * existing package is updated in place. Per-field shape validation
     * (blank name, negative price, etc.) is already enforced by @Valid on
     * PackageRequest before this method ever runs - this method only checks
     * the one rule that needs a database lookup: the name must be unique.
     * Existing buyers of a package that later has its price/terms changed
     * are NOT retroactively affected - their Subscription already recorded
     * its own end date and price at purchase time.
     */
    public Package savePackage(PackageRequest request) {
        request.setName(request.getName().trim());
        Optional<Package> sameName = packageRepository.findByName(request.getName());

        if (request.getId() == null) {
            if (sameName.isPresent()) {
                throw new DuplicateResourceException("Package with name " + request.getName() + " already exists");
            }
            Package pkg = new Package();
            request.applyTo(pkg);
            applyAvailability(request, pkg);
            applyBenefits(request, pkg, List.copyOf(pkg.getFacilities()));
            Package saved = packageRepository.save(pkg);
            log.info("Created package id={} name='{}'", saved.getId(), saved.getName());
            return saved;
        }

        if (sameName.isPresent() && !sameName.get().getId().equals(request.getId())) {
            throw new DuplicateResourceException("Package with name " + request.getName() + " already exists");
        }

        Package existing = packageRepository.findById(request.getId())
                .filter(pkg -> !pkg.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with id: " + request.getId()));

        request.applyTo(existing);
        applyAvailability(request, existing);
        applyBenefits(request, existing, List.copyOf(existing.getFacilities()));
        Package saved = packageRepository.save(existing);
        log.info("Updated package id={} name='{}'", saved.getId(), saved.getName());
        return saved;
    }

    private void applyAvailability(PackageRequest request, Package pkg) {
        if (facilityRepository == null || facilityServiceRepository == null) {
            return;
        }

        List<Long> facilityIds = request.getFacilityIds() == null ? List.of() : request.getFacilityIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (!request.isAllFacilities() && facilityIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one parking complex, or choose all complexes.");
        }

        List<ParkingFacility> facilities = request.isAllFacilities() ? List.of() : facilityRepository.findAllById(facilityIds);
        if (!request.isAllFacilities() && facilities.size() != facilityIds.size()) {
            throw new IllegalArgumentException("One or more selected parking complexes no longer exist.");
        }
        // Existing links to an inactive complex are retained on edit, but an
        // inactive complex may never be newly assigned.
        Set<Long> existingFacilityIds = pkg.getFacilities().stream().map(ParkingFacility::getId).collect(java.util.stream.Collectors.toSet());
        for (ParkingFacility facility : facilities) {
            if (!facility.isActive() && !existingFacilityIds.contains(facility.getId())) {
                throw new IllegalArgumentException("Inactive parking complexes cannot be newly assigned to a membership.");
            }
        }

        List<Long> serviceIds = request.getServiceIds() == null ? List.of() : request.getServiceIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        List<FacilityService> services = facilityServiceRepository.findAllById(serviceIds);
        if (services.size() != serviceIds.size()) {
            throw new IllegalArgumentException("One or more selected services no longer exist.");
        }
        if (!request.isAllFacilities()) {
            for (FacilityService service : services) {
                if (service.getFacilities().isEmpty() || service.getFacilities().stream().noneMatch(serviceFacility -> facilities.stream().anyMatch(f -> f.getId().equals(serviceFacility.getId())))) {
                    throw new IllegalArgumentException("Service '" + service.getName() + "' does not belong to a selected parking complex.");
                }
            }
        }

        pkg.setAllFacilities(request.isAllFacilities());
        pkg.setFacilities(new LinkedHashSet<>(facilities));
        pkg.setIncludedServiceItems(new LinkedHashSet<>(services));
        pkg.setIncludedServices(services.stream().map(FacilityService::getName).collect(java.util.stream.Collectors.joining(", ")));
    }

    private void applyBenefits(PackageRequest request, Package pkg, List<ParkingFacility> selectedFacilities) {
        List<MembershipBenefitRequest> requests = request.getBenefits() == null ? List.of() : request.getBenefits().stream()
                .filter(java.util.Objects::nonNull).toList();
        Set<MembershipBenefit> benefits = new LinkedHashSet<>();
        for (MembershipBenefitRequest requestBenefit : requests) {
            if (requestBenefit.getType() == null) {
                throw new IllegalArgumentException("Choose a benefit type, or remove the empty benefit before saving.");
            }
            if (!requestBenefit.getType().isConfigurable()) {
                throw new IllegalArgumentException("This benefit type is no longer supported. Choose another membership benefit.");
            }
            MembershipBenefit benefit = new MembershipBenefit();
            benefit.setType(requestBenefit.getType());
            benefit.setValueAmount(requestBenefit.getValueAmount());
            benefit.setQualifyingCount(requestBenefit.getQualifyingCount());
            benefit.setAllowanceMinutes(requestBenefit.getAllowanceMinutes());
            benefit.setRewardDescription(requestBenefit.getRewardDescription() == null ? null : requestBenefit.getRewardDescription().trim());
            benefit.setFrequency(requestBenefit.getFrequency() == null ? MembershipBenefit.Frequency.UNLIMITED : requestBenefit.getFrequency());
            FacilityService service = null;
            if (requestBenefit.getServiceId() != null) {
                if (facilityServiceRepository == null) throw new IllegalArgumentException("Benefit services are unavailable. Please try again later.");
                service = facilityServiceRepository.findById(requestBenefit.getServiceId())
                        .orElseThrow(() -> new IllegalArgumentException("Selected benefit service no longer exists."));
                if (!service.isAvailable()) throw new IllegalArgumentException("A membership benefit cannot use an unavailable service.");
                boolean relevant = request.isAllFacilities()
                        ? service.getFacilities().stream().anyMatch(ParkingFacility::isActive)
                        : service.getFacilities().stream().anyMatch(sf -> selectedFacilities.stream().anyMatch(f -> f.getId().equals(sf.getId())));
                if (!relevant) throw new IllegalArgumentException("Selected benefit service must be available at a participating complex.");
            }
            validateBenefit(requestBenefit, service);
            benefit.setFacilityService(service);
            benefits.add(benefit);
        }
        pkg.setBenefits(benefits);
    }

    private void validateBenefit(MembershipBenefitRequest benefit, FacilityService service) {
        switch (benefit.getType()) {
            case PARKING_DISCOUNT -> requirePositive(benefit.getValueAmount(), "Parking discount");
            case NTH_PARKING_FREE -> { if (benefit.getQualifyingCount() == null || benefit.getQualifyingCount() < 1) throw new IllegalArgumentException("Nth parking benefit needs a positive visit count."); }
            case EV_CHARGING_ALLOWANCE, PARKING_ALLOWANCE -> { if (benefit.getAllowanceMinutes() == null || benefit.getAllowanceMinutes() < 1) throw new IllegalArgumentException("Allowance minutes must be positive."); if (benefit.getType() == MembershipBenefit.BenefitType.EV_CHARGING_ALLOWANCE && service == null) throw new IllegalArgumentException("EV charging allowance requires a participating charging service."); }
            case FREE_OR_DISCOUNTED_SERVICE -> { if (service == null) throw new IllegalArgumentException("A service benefit requires a participating facility service."); if (benefit.getValueAmount() != null) requirePositive(benefit.getValueAmount(), "Service discount"); }
            case PARTNER_REWARD -> throw new IllegalArgumentException("Partner rewards are no longer supported. Choose another membership benefit.");
        }
        if (benefit.getValueAmount() != null && benefit.getValueAmount().compareTo(BigDecimal.valueOf(100)) > 0) throw new IllegalArgumentException("Benefit discount cannot exceed 100%.");
    }
    private void requirePositive(BigDecimal value, String label) { if (value == null || value.signum() <= 0) throw new IllegalArgumentException(label + " must be positive."); }

    public Package toggleStatus(Long id) {
        Package pkg = packageRepository.findById(id)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with id: " + id));
        pkg.setActive(!pkg.isActive());
        Package saved = packageRepository.save(pkg);
        log.info("Toggled package id={} to active={}", saved.getId(), saved.isActive());
        return saved;
    }

    public Package getPackageById(Long id) {
        return packageRepository.findById(id)
                .filter(pkg -> !pkg.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with id: " + id));
    }

    public List<Package> getAllPackages() {
        return packageRepository.findAll().stream().filter(pkg -> !pkg.isDeleted()).toList();
    }

    public List<Package> getActivePackages() {
        return packageRepository.findByStatus(Package.PackageStatus.ACTIVE).stream().filter(pkg -> !pkg.isDeleted()).toList();
    }

    /**
     * Remove unused packages outright. Retain purchased packages as hidden
     * records so existing subscriptions and their benefit history still work.
     */
    public void deletePackage(Long id) {
        Package pkg = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with id: " + id));

        if (subscriptionRepository != null && subscriptionRepository.existsByMembershipPackage_Id(id)) {
            pkg.setDeleted(true);
            pkg.setActive(false);
            packageRepository.saveAndFlush(pkg);
            log.info("Removed package id={} from catalog; retained purchased memberships", id);
            return;
        }

        try {
            packageRepository.delete(pkg);
            packageRepository.flush();
            log.info("Deleted package id={}", id);
        } catch (DataIntegrityViolationException e) {
            log.warn("Blocked delete of package id={} - database integrity constraint violated", id);
            throw new ResourceInUseException("This package has linked customer records and cannot be deleted - deactivate it instead.");
        } catch (Exception e) {
            log.error("Failed to delete package id={}", id, e);
            throw new ResourceInUseException("Package cannot be deleted due to associated records. Deactivate it instead.");
        }
    }

    // --- Legacy entity-based API (kept for backward compatibility; no
    // longer used by the web layer, which now goes through
    // savePackage(PackageRequest) above) ---

    public Package createPackage(Package pkg) throws Exception {
        validate(pkg);
        if (packageRepository.findByName(pkg.getName()).isPresent()) {
            throw new Exception("Package with name " + pkg.getName() + " already exists");
        }
        return packageRepository.save(pkg);
    }

    private void validate(Package pkg) throws Exception {
        if (pkg.getName() == null || pkg.getName().isBlank()) {
            throw new Exception("Package name is required");
        }
        if (pkg.getPrice() == null || pkg.getPrice().signum() <= 0) {
            throw new Exception("Price must be greater than 0");
        }
        if (pkg.getValidityDays() == null || pkg.getValidityDays() <= 0) {
            throw new Exception("Duration must be at least 1 day");
        }
        if (pkg.getDiscountPercentage() != null
                && (pkg.getDiscountPercentage().signum() < 0
                    || pkg.getDiscountPercentage().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new Exception("Discount percentage must be between 0 and 100");
        }
    }
}
