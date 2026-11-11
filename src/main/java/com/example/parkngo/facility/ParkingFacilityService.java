package com.example.parkngo.facility;

import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationRepository;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

// [ Service Layer & DI]: Encapsulates facility business logic; repositories are injected via constructor injection.
// [ Transaction Management]: @Transactional ensures ACID compliance; rolls back DB operations on unhandled runtime exceptions.
@Service
@Transactional
public class ParkingFacilityService {
    private static final Logger log = LoggerFactory.getLogger(ParkingFacilityService.class);
    public static final String PHONE_REGEX = "^\\+94[ -]?(?:[0-9]{9}|[0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{4}|[0-9]{2}[ -]?[0-9]{7}|[0-9]{3}[ -]?[0-9]{3}[ -]?[0-9]{3})$";
    private final ParkingFacilityRepository facilityRepository;
    private final ParkingSpaceRepository parkingSpaceRepository;
    private final FacilityServiceRepository facilityServiceRepository;
    private final ReservationRepository reservationRepository;

    @Autowired(required = false)
    private com.example.parkngo.user.AdminRepository adminRepository;

    @Autowired(required = false)
    private com.example.parkngo.user.FacilityStaffAccountService facilityStaffAccountService;

    @Autowired(required = false)
    private com.example.parkngo.pattern.strategy.SpaceAllocationContext spaceAllocationContext;

    public ParkingFacilityService(ParkingFacilityRepository facilityRepository,
                                  ParkingSpaceRepository parkingSpaceRepository,
                                  FacilityServiceRepository facilityServiceRepository,
                                  ReservationRepository reservationRepository) {
        this.facilityRepository = facilityRepository;
        this.parkingSpaceRepository = parkingSpaceRepository;
        this.facilityServiceRepository = facilityServiceRepository;
        this.reservationRepository = reservationRepository;
    }

    // [ Business Validation]: Enforces uniqueness, format constraints, and default capacity before persisting.
    public ParkingFacility createFacility(ParkingFacility facility) throws Exception {
        if (facility == null) {
            throw new IllegalArgumentException("Facility details are required");
        }
        if (facility.getName() == null || facility.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Facility name is required");
        }
        String name = facility.getName().trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new IllegalArgumentException("Facility name must be between 2 and 100 characters");
        }
        facility.setName(name);

        if (facilityRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new IllegalArgumentException("Facility with this name already exists");
        }

        if (facility.getAddress() == null || facility.getAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("Facility location / address is required");
        }
        String address = facility.getAddress().trim();
        if (address.length() < 10) {
            throw new IllegalArgumentException("Facility location / address must be at least 10 characters");
        }
        if (address.length() > 255) {
            throw new IllegalArgumentException("Address cannot exceed 255 characters");
        }
        facility.setAddress(address);

        if (facility.getContactNumber() == null || facility.getContactNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Contact number is required");
        }
        facility.setContactNumber(facility.getContactNumber().trim());
        if (!facility.getContactNumber().matches(PHONE_REGEX)) {
            throw new IllegalArgumentException("Invalid contact number. Must start with +94 followed by 9 digits (e.g., +94771234567 or +94-11-2345678)");
        }

        if (facility.getDescription() == null || facility.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required");
        }
        String desc = facility.getDescription().trim();
        if (desc.length() < 10) {
            throw new IllegalArgumentException("Description must be at least 10 characters");
        }
        facility.setDescription(desc);

        if (facility.getOpeningTime() == null) {
            facility.setOpeningTime(LocalTime.of(6, 0));
        }
        if (facility.getClosingTime() == null) {
            facility.setClosingTime(LocalTime.of(23, 0));
        }
        if (!facility.getClosingTime().isAfter(facility.getOpeningTime())) {
            throw new IllegalArgumentException("Closing time must be after opening time");
        }

        if (facility.getTotalCapacity() == null) {
            facility.setTotalCapacity(100);
        } else if (facility.getTotalCapacity() < 1 || facility.getTotalCapacity() > 5000) {
            throw new IllegalArgumentException("Total capacity must be between 1 and 5000");
        }

        if (facility.getCurrentOccupancy() == null) {
            facility.setCurrentOccupancy(0);
        }
        if (facility.getStatus() == null) {
            facility.setStatus(ParkingFacility.FacilityStatus.ACTIVE);
        }
        if (facility.getTotalFloors() == null) {
            facility.setTotalFloors(1);
        } else if (facility.getTotalFloors() < 1 || facility.getTotalFloors() > 10) {
            throw new IllegalArgumentException("Number of floors must be a whole number between 1 and 10");
        }
        sanitizeFacilityData(facility);
        ParkingFacility saved = facilityRepository.save(facility);
        if (facilityStaffAccountService != null) {
            facilityStaffAccountService.ensureStaffAccount(saved);
        }
        try {
            generateDefaultSpacesForFacility(saved);
        } catch (Exception e) {
            log.warn("Facility {} was created, but default parking-space generation failed: {}", saved.getId(), e.getMessage());
        }
        return saved;
    }

    public void generateDefaultSpacesForFacility(ParkingFacility facility) {
        if (facility == null || facility.getId() == null) return;
        long currentCount = parkingSpaceRepository.countByFacilityId(facility.getId());
        if (currentCount > 0) {
            return;
        }

        int totalFloors = facility.getTotalFloors() != null && facility.getTotalFloors() >= 1
                ? facility.getTotalFloors()
                : 1;

        char[] zones = new char[]{'A', 'B', 'C', 'D', 'E'};
        for (int zIdx = 0; zIdx < zones.length; zIdx++) {
            char zoneChar = zones[zIdx];
            String zoneName = "Zone " + zoneChar;
            int floor = Math.min(totalFloors, (zIdx % totalFloors) + 1);

            for (int slot = 1; slot <= 20; slot++) {
                String preferredCode = String.format("%c-%02d", zoneChar, slot);
                String bayCode = preferredCode;
                if (parkingSpaceRepository.findBySpaceNumber(bayCode).isPresent()) {
                    bayCode = String.format("F%d-%c-%02d", facility.getId(), zoneChar, slot);
                    if (parkingSpaceRepository.findBySpaceNumber(bayCode).isPresent()) {
                        continue;
                    }
                }

                ParkingSpace ps = new ParkingSpace();
                ps.setSpaceNumber(bayCode);
                ps.setFloor(floor);
                ps.setZone(zoneName);
                ps.setParkingType("STANDARD");
                ps.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
                ps.setHourlyRate(BigDecimal.valueOf(250.00));
                ps.setHasEVCharging(slot % 5 == 0);
                ps.setIsAccessible(slot % 4 == 0);
                ps.setAllocationType("GENERAL");
                ps.setFacility(facility);
                try {
                    parkingSpaceRepository.save(ps);
                } catch (Exception e) {
                    log.warn("Unable to create default space {} for facility {}: {}", bayCode, facility.getId(), e.getMessage());
                }
            }
        }
    }

    public void syncFacilityOccupancy(ParkingFacility facility) {
        if (facility == null || facility.getId() == null) return;
        long occupied = 0;
        long reserved = 0;
        if (parkingSpaceRepository != null) {
            try {
                occupied = parkingSpaceRepository.countByFacilityIdAndStatus(facility.getId(), ParkingSpace.ParkingSpaceStatus.OCCUPIED);
                reserved = parkingSpaceRepository.countByFacilityIdAndStatus(facility.getId(), ParkingSpace.ParkingSpaceStatus.RESERVED);
            } catch (Exception ignored) {}
        }
        long activeReservations = 0;
        if (reservationRepository != null) {
            try {
                activeReservations = reservationRepository.countActiveReservationsByFacilityId(facility.getId());
            } catch (Exception ignored) {}
        }
        int totalOcc = (int) Math.max(occupied + reserved, activeReservations);
        if (facility.getTotalCapacity() != null && totalOcc > facility.getTotalCapacity()) {
            totalOcc = facility.getTotalCapacity();
        }
        facility.setCurrentOccupancy(totalOcc);
    }

    public void syncFacilityOccupancy(Long facilityId) {
        if (facilityId == null || facilityRepository == null) return;
        try {
            facilityRepository.findById(facilityId).ifPresent(f -> {
                syncFacilityOccupancy(f);
                facilityRepository.save(f);
            });
        } catch (Exception ignored) {}
    }

    public ParkingFacility getFacilityById(Long id) throws Exception {
        return facilityRepository.findById(id)
                .orElseThrow(() -> new Exception("Facility not found"));
    }

    /**
     * Default getAllFacilities returns only ACTIVE facilities for customer & default queries.
     */
    // [ Customer Deactivation Filter]: Returns only ACTIVE facilities so deactivated complexes are hidden from customer booking.
    public List<ParkingFacility> getAllFacilities() {
        List<ParkingFacility> list = facilityRepository.findByStatus(ParkingFacility.FacilityStatus.ACTIVE);
        for (ParkingFacility f : list) {
            syncFacilityOccupancy(f);
        }
        return list;
    }

    /**
     * Admin view: get all facilities including inactive.
     */
    // [ Admin All-Inclusive Query]: Returns both ACTIVE and INACTIVE facilities for administrator management.
    public List<ParkingFacility> getAllFacilitiesIncludingInactive() {
        List<ParkingFacility> list = facilityRepository.findAll();
        for (ParkingFacility f : list) {
            syncFacilityOccupancy(f);
        }
        return list;
    }

    public ParkingFacility updateFacility(ParkingFacility facility) throws Exception {
        if (facility.getId() == null) {
            throw new IllegalArgumentException("Facility ID must not be null for update");
        }
        return updateFacility(facility.getId(), facility);
    }

    public ParkingFacility updateFacility(Long id, ParkingFacility facility) throws Exception {
        ParkingFacility existing = getFacilityById(id);
        if (facility.getName() != null && !facility.getName().trim().isEmpty()) {
            String name = facility.getName().trim();
            if (name.length() < 2 || name.length() > 100) {
                throw new IllegalArgumentException("Facility name must be between 2 and 100 characters");
            }
            if (facilityRepository.findByNameIgnoreCaseAndIdNot(name, id).isPresent()) {
                throw new IllegalArgumentException("Another facility with this name already exists");
            }
            existing.setName(name);
        }

        if (facility.getAddress() != null) {
            String addr = facility.getAddress().trim();
            if (addr.isEmpty()) {
                throw new IllegalArgumentException("Address cannot be empty");
            }
            if (addr.length() < 10) {
                throw new IllegalArgumentException("Facility location / address must be at least 10 characters");
            }
            if (addr.length() > 255) {
                throw new IllegalArgumentException("Address cannot exceed 255 characters");
            }
            existing.setAddress(addr);
        }

        if (facility.getContactNumber() != null) {
            String contact = facility.getContactNumber().trim();
            if (contact.isEmpty()) {
                throw new IllegalArgumentException("Contact number cannot be empty");
            }
            if (!contact.matches(PHONE_REGEX)) {
                throw new IllegalArgumentException("Invalid contact number. Must start with +94 followed by 9 digits (e.g., +94771234567 or +94-11-2345678)");
            }
            existing.setContactNumber(contact);
        }

        LocalTime openTime = facility.getOpeningTime() != null ? facility.getOpeningTime() : existing.getOpeningTime();
        LocalTime closeTime = facility.getClosingTime() != null ? facility.getClosingTime() : existing.getClosingTime();
        if (openTime != null && closeTime != null && !closeTime.isAfter(openTime)) {
            throw new IllegalArgumentException("Closing time must be after opening time");
        }
        if (facility.getOpeningTime() != null) {
            existing.setOpeningTime(facility.getOpeningTime());
        }
        if (facility.getClosingTime() != null) {
            existing.setClosingTime(facility.getClosingTime());
        }

        if (facility.getTotalCapacity() != null) {
            int newCap = facility.getTotalCapacity();
            if (newCap < 1 || newCap > 5000) {
                throw new IllegalArgumentException("Total capacity must be between 1 and 5000");
            }
            int currentOcc = existing.getCurrentOccupancy() != null ? existing.getCurrentOccupancy() : 0;
            if (newCap < currentOcc) {
                throw new IllegalArgumentException("Total capacity (" + newCap + ") cannot be less than current occupancy (" + currentOcc + "). Minimum allowed is " + currentOcc + ".");
            }
            existing.setTotalCapacity(newCap);
        }

        if (facility.getTotalFloors() != null) {
            int newFloors = facility.getTotalFloors();
            if (newFloors < 1 || newFloors > 10) {
                throw new IllegalArgumentException("Number of floors must be a whole number between 1 and 10.");
            }
            Integer maxFloor = parkingSpaceRepository.findMaxFloorByFacilityId(id);
            if (maxFloor != null && newFloors < maxFloor) {
                throw new IllegalArgumentException("Cannot reduce floor count to " + newFloors + " because parking spaces already exist on Floor " + maxFloor + ". Please reassign or remove those spaces first.");
            }
            existing.setTotalFloors(newFloors);
        }

        if (facility.getStatus() != null && facility.getStatus() != existing.getStatus()) {
            if (facility.getStatus() == ParkingFacility.FacilityStatus.INACTIVE) {
                validateFacilityCanBeDeactivated(id);
            }
            existing.setStatus(facility.getStatus());
        }
        if (facility.getDescription() != null) {
            String desc = facility.getDescription().trim();
            if (desc.isEmpty()) {
                throw new IllegalArgumentException("Description cannot be empty");
            }
            if (desc.length() < 10) {
                throw new IllegalArgumentException("Description must be at least 10 characters");
            }
            existing.setDescription(desc);
        }
        return facilityRepository.save(existing);
    }

    public List<ParkingFacility> searchFacilities(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllFacilities();
        }
        String term = keyword.trim();
        List<ParkingFacility> list = facilityRepository.findByNameContainingIgnoreCaseAndStatusOrAddressContainingIgnoreCaseAndStatus(
                term, ParkingFacility.FacilityStatus.ACTIVE,
                term, ParkingFacility.FacilityStatus.ACTIVE
        );
        for (ParkingFacility f : list) {
            syncFacilityOccupancy(f);
        }
        return list;
    }

    public List<ParkingFacility> searchFacilities(String keyword, boolean includeInactive) {
        if (!includeInactive) {
            return searchFacilities(keyword);
        }
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllFacilitiesIncludingInactive();
        }
        String term = keyword.trim();
        List<ParkingFacility> list = facilityRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(term, term);
        for (ParkingFacility f : list) {
            syncFacilityOccupancy(f);
        }
        return list;
    }

    public void updateOccupancy(Long facilityId, int delta) throws Exception {
        ParkingFacility facility = getFacilityById(facilityId);
        int current = facility.getCurrentOccupancy() != null ? facility.getCurrentOccupancy() : 0;
        int newOccupancy = current + delta;
        if (newOccupancy < 0) newOccupancy = 0;
        if (facility.getTotalCapacity() != null && newOccupancy > facility.getTotalCapacity()) {
            newOccupancy = facility.getTotalCapacity();
        }
        facility.setCurrentOccupancy(newOccupancy);
        facilityRepository.save(facility);
    }

    /**
     * Validates whether a facility can be deactivated (inactivated).
     * 
     * Rule:
     * When a facility has active customer slot bookings (active, confirmed, verified, or pending reservations,
     * or occupied/reserved parking bays, or current occupancy > 0), the admin CANNOT inactivate it.
     * When there are no slot bookings, the facility CAN be activated or inactivated freely.
     */
    public void validateFacilityCanBeDeactivated(Long facilityId) throws Exception {
        ParkingFacility facility = getFacilityById(facilityId);

        // 1. Check for active/upcoming customer reservations
        long activeBookingsCount = 0;
        if (reservationRepository != null) {
            try {
                List<Reservation> allReservations = reservationRepository.findAll();
                if (allReservations != null) {
                    activeBookingsCount = allReservations.stream()
                            .filter(r -> r.getParkingSpace() != null &&
                                         r.getParkingSpace().getFacility() != null &&
                                         facilityId.equals(r.getParkingSpace().getFacility().getId()))
                            .filter(r -> r.getStatus() == Reservation.ReservationStatus.ACTIVE ||
                                         r.getStatus() == Reservation.ReservationStatus.CONFIRMED ||
                                         r.getStatus() == Reservation.ReservationStatus.PENDING ||
                                         r.getStatus() == Reservation.ReservationStatus.VERIFIED ||
                                         (r.getDepartureTime() != null && r.getDepartureTime().isAfter(java.time.LocalDateTime.now()) &&
                                          r.getStatus() != Reservation.ReservationStatus.CANCELLED &&
                                          r.getStatus() != Reservation.ReservationStatus.COMPLETED &&
                                          r.getStatus() != Reservation.ReservationStatus.EXPIRED))
                            .count();
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Check for occupied or reserved parking bays
        long occupiedSpaces = parkingSpaceRepository.countByFacilityIdAndStatus(facilityId, ParkingSpace.ParkingSpaceStatus.OCCUPIED);
        long reservedSpaces = parkingSpaceRepository.countByFacilityIdAndStatus(facilityId, ParkingSpace.ParkingSpaceStatus.RESERVED);
        int currentOcc = facility.getCurrentOccupancy() != null ? facility.getCurrentOccupancy() : 0;

        if (activeBookingsCount > 0 || occupiedSpaces > 0 || reservedSpaces > 0 || currentOcc > 0) {
            long totalActive = Math.max(activeBookingsCount, occupiedSpaces + reservedSpaces);
            if (totalActive == 0) totalActive = currentOcc;
            throw new IllegalStateException("Cannot inactivate facility '" + facility.getName() + "': there are " + totalActive + " active customer slot booking(s) / reservation(s) or parked vehicle(s). A facility cannot be inactivated while customers have active or scheduled bookings.");
        }
    }

    /**
     * Soft delete (Deactivate) facility.
     * Guarded against active use: throws exception if facility has occupied or reserved spaces.
     */
    // [ Safe Deactivation / Soft Delete]: Inactivates facility only if there are no active/overstaying reservations or occupied bays.
    public void deactivateFacility(Long id) throws Exception {
        validateFacilityCanBeDeactivated(id);
        ParkingFacility facility = getFacilityById(id);
        sanitizeFacilityData(facility);
        facility.setStatus(ParkingFacility.FacilityStatus.INACTIVE);
        facilityRepository.save(facility);
    }

    /**
     * Reactivate an inactive facility.
     */
    public void reactivateFacility(Long id) throws Exception {
        ParkingFacility facility = getFacilityById(id);
        sanitizeFacilityData(facility);
        facility.setStatus(ParkingFacility.FacilityStatus.ACTIVE);
        facilityRepository.save(facility);
    }

    private void sanitizeFacilityData(ParkingFacility facility) {
        if (facility == null) return;
        if (facility.getContactNumber() == null || !facility.getContactNumber().matches(PHONE_REGEX)) {
            facility.setContactNumber("+94-11-2345678");
        }
        if (facility.getOpeningTime() == null) {
            facility.setOpeningTime(LocalTime.of(6, 0));
        }
        if (facility.getClosingTime() == null) {
            facility.setClosingTime(LocalTime.of(23, 0));
        }
        if (facility.getTotalCapacity() == null || facility.getTotalCapacity() <= 0) {
            facility.setTotalCapacity(100);
        }
        if (facility.getTotalFloors() == null || facility.getTotalFloors() < 1) {
            facility.setTotalFloors(1);
        }
        if (facility.getCurrentOccupancy() == null) {
            facility.setCurrentOccupancy(0);
        }
        if (facility.getStatus() == null) {
            facility.setStatus(ParkingFacility.FacilityStatus.ACTIVE);
        }
        if (facility.getManagedByAdminId() == null || facility.getManagedByAdminId() <= 0) {
            facility.setManagedByAdminId(resolveAdminId());
        }
        facility.ensureAddress();
    }

    private Long resolveAdminId() {
        if (adminRepository != null) {
            try {
                return adminRepository.findAll().stream()
                        .map(com.example.parkngo.user.Admin::getId)
                        .findFirst()
                        .orElse(12L);
            } catch (Exception ignored) {}
        }
        return 12L;
    }

    /**
     * Toggle active/inactive status.
     */
    public ParkingFacility toggleFacilityStatus(Long id) throws Exception {
        ParkingFacility facility = getFacilityById(id);
        if (facility.getStatus() == ParkingFacility.FacilityStatus.ACTIVE) {
            deactivateFacility(id);
        } else {
            reactivateFacility(id);
        }
        return getFacilityById(id);
    }

    /**
     * Task 1 requirement: deleteFacility delegates to deactivateFacility (soft-delete).
     */
    public void deleteFacility(Long id) throws Exception {
        deactivateFacility(id);
    }

    /**
     * True hard delete for edge cases (zero linked parking spaces).
     */
    public void hardDeleteFacility(Long id) throws Exception {
        if (!facilityRepository.existsById(id)) {
            throw new Exception("Facility not found");
        }
        long spaceCount = parkingSpaceRepository.countByFacilityId(id);
        if (spaceCount > 0) {
            throw new IllegalStateException("Cannot permanently delete facility because " + spaceCount + " linked parking spaces exist. Deactivate the facility instead.");
        }
        if (facilityServiceRepository != null) {
            long serviceCount = facilityServiceRepository.countByFacilityId(id);
            if (serviceCount > 0) {
                throw new IllegalStateException("Cannot permanently delete facility because " + serviceCount + " linked services exist. Deactivate the facility instead.");
            }
        }
        if (reservationRepository != null) {
            long reservationCount = reservationRepository.countByParkingSpaceFacilityId(id);
            if (reservationCount > 0) {
                throw new IllegalStateException("Cannot permanently delete facility because it has reservation history. Deactivate the facility instead.");
            }
        }
        facilityRepository.deleteById(id);
    }

    public long countFacilities() {
        return facilityRepository.count();
    }

    public long countActiveFacilities() {
        return facilityRepository.findByStatus(ParkingFacility.FacilityStatus.ACTIVE).size();
    }

    /**
     * Allocates the optimal parking space in the facility using the Strategy Pattern
     * (SpaceAllocationContext) based on dynamic user requirements.
     */
    // [VIVA - Strategy Pattern Invocation]: Delegates space selection to SpaceAllocationContext based on dynamic user criteria.
    public java.util.Optional<ParkingSpace> allocateOptimalSpace(Long facilityId, com.example.parkngo.pattern.strategy.AllocationCriteria criteria) {
        if (facilityId == null) {
            return java.util.Optional.empty();
        }
        List<ParkingSpace> available = parkingSpaceRepository.findByFacilityIdAndStatus(
                facilityId, ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        if (spaceAllocationContext != null) {
            return spaceAllocationContext.allocateSpace(available, criteria);
        }
        return available.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(available.get(0));
    }

    /**
     * Allocates the optimal parking space using a specific explicit allocation strategy.
     */
    public java.util.Optional<ParkingSpace> allocateOptimalSpace(Long facilityId, com.example.parkngo.pattern.strategy.AllocationCriteria criteria,
                                                               com.example.parkngo.pattern.strategy.AllocationStrategyType strategyType) {
        if (facilityId == null) {
            return java.util.Optional.empty();
        }
        List<ParkingSpace> available = parkingSpaceRepository.findByFacilityIdAndStatus(
                facilityId, ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        if (spaceAllocationContext != null) {
            return spaceAllocationContext.allocateSpace(available, criteria, strategyType);
        }
        return available.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(available.get(0));
    }

    public void setSpaceAllocationContext(com.example.parkngo.pattern.strategy.SpaceAllocationContext spaceAllocationContext) {
        this.spaceAllocationContext = spaceAllocationContext;
    }
}

