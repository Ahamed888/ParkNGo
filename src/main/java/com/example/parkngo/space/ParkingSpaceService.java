package com.example.parkngo.space;

import com.example.parkngo.reservation.ReservationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ParkingSpaceService {
    private final ParkingSpaceRepository parkingSpaceRepository;
    private final ReservationRepository reservationRepository;

    public ParkingSpaceService(ParkingSpaceRepository parkingSpaceRepository,
                               ReservationRepository reservationRepository) {
        this.parkingSpaceRepository = parkingSpaceRepository;
        this.reservationRepository = reservationRepository;
    }

    public ParkingSpace createParkingSpace(ParkingSpace space) throws Exception {
        if (space.getSpaceNumber() == null || space.getSpaceNumber().trim().isEmpty()) {
            throw new Exception("Space code / space number is required");
        }
        String spaceNum = space.getSpaceNumber().trim().toUpperCase();
        space.setSpaceNumber(spaceNum);
        if (parkingSpaceRepository.findBySpaceNumber(spaceNum).isPresent()) {
            throw new Exception("Space code '" + spaceNum + "' already exists. Please choose a unique space code.");
        }
        if (space.getStatus() == null) {
            space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        }
        if (space.getParkingType() != null) {
            String pt = space.getParkingType().trim().toUpperCase();
            if ("ELECTRIC".equals(pt)) {
                space.setParkingType("EV_CHARGING");
            } else if ("HANDICAPPED".equals(pt)) {
                space.setParkingType("ACCESSIBLE");
            } else {
                space.setParkingType(pt);
            }
        }
        if (space.getHourlyRate() == null) {
            String type = space.getParkingType() != null ? space.getParkingType().toUpperCase() : "STANDARD";
            java.math.BigDecimal defaultRate = switch (type) {
                case "ACCESSIBLE", "HANDICAPPED" -> java.math.BigDecimal.valueOf(150);
                case "COMPACT" -> java.math.BigDecimal.valueOf(300);
                case "ELECTRIC", "EV_CHARGING" -> java.math.BigDecimal.valueOf(400);
                case "VIP" -> java.math.BigDecimal.valueOf(500);
                default -> java.math.BigDecimal.valueOf(250);
            };
            space.setHourlyRate(defaultRate);
        }
        if (space.getFacility() != null) {
            int maxFloor = space.getFacility().getTotalFloors() != null && space.getFacility().getTotalFloors() >= 1
                    ? space.getFacility().getTotalFloors() : 1;
            if (space.getFloor() == null || space.getFloor() < 1 || space.getFloor() > maxFloor) {
                throw new IllegalArgumentException("Invalid floor level: " + space.getFloor() + ". Facility '" 
                        + space.getFacility().getName() + "' only has " + maxFloor + " floor level(s) (Floor 1 to " + maxFloor + ").");
            }
        } else if (space.getFloor() == null || space.getFloor() < 1) {
            space.setFloor(1);
        }
        if (space.getHasEVCharging() == null) {
            space.setHasEVCharging("ELECTRIC".equalsIgnoreCase(space.getParkingType()) || "EV_CHARGING".equalsIgnoreCase(space.getParkingType()));
        }
        if (space.getIsAccessible() == null) {
            space.setIsAccessible("HANDICAPPED".equalsIgnoreCase(space.getParkingType()) || "ACCESSIBLE".equalsIgnoreCase(space.getParkingType()));
        }
        return parkingSpaceRepository.save(space);
    }

    public ParkingSpace updateParkingSpace(Long id, ParkingSpace updated) throws Exception {
        ParkingSpace existing = getParkingSpaceById(id);
        String spaceNum = updated.getSpaceNumber() != null ? updated.getSpaceNumber().trim().toUpperCase() : existing.getSpaceNumber();
        if (parkingSpaceRepository.findBySpaceNumberAndIdNot(spaceNum, id).isPresent()) {
            throw new Exception("Space code '" + spaceNum + "' is already in use by another space.");
        }
        com.example.parkngo.facility.ParkingFacility facility = updated.getFacility() != null ? updated.getFacility() : existing.getFacility();
        if (facility != null) {
            int maxFloor = facility.getTotalFloors() != null && facility.getTotalFloors() >= 1
                    ? facility.getTotalFloors() : 1;
            Integer newFloor = updated.getFloor() != null ? updated.getFloor() : existing.getFloor();
            if (newFloor == null || newFloor < 1 || newFloor > maxFloor) {
                throw new IllegalArgumentException("Invalid floor level: " + newFloor + ". Facility '" 
                        + facility.getName() + "' only has " + maxFloor + " floor level(s) (Floor 1 to " + maxFloor + ").");
            }
            existing.setFloor(newFloor);
        } else {
            existing.setFloor(updated.getFloor() != null ? updated.getFloor() : 1);
        }
        existing.setSpaceNumber(spaceNum);
        existing.setZone(updated.getZone());
        if (updated.getParkingType() != null) {
            String pt = updated.getParkingType().trim().toUpperCase();
            if ("ELECTRIC".equals(pt)) {
                existing.setParkingType("EV_CHARGING");
            } else if ("HANDICAPPED".equals(pt)) {
                existing.setParkingType("ACCESSIBLE");
            } else {
                existing.setParkingType(pt);
            }
        }
        existing.setHourlyRate(updated.getHourlyRate());
        existing.setHasEVCharging(updated.getHasEVCharging() != null ? updated.getHasEVCharging() : ("ELECTRIC".equalsIgnoreCase(updated.getParkingType()) || "EV_CHARGING".equalsIgnoreCase(updated.getParkingType())));
        existing.setIsAccessible(updated.getIsAccessible() != null ? updated.getIsAccessible() : ("HANDICAPPED".equalsIgnoreCase(updated.getParkingType()) || "ACCESSIBLE".equalsIgnoreCase(updated.getParkingType())));
        if (updated.getStatus() != null) {
            existing.setStatus(updated.getStatus());
        }
        if (updated.getFacility() != null) {
            existing.setFacility(updated.getFacility());
        }
        return parkingSpaceRepository.save(existing);
    }

    public ParkingSpace getParkingSpaceById(Long id) throws Exception {
        return parkingSpaceRepository.findById(id)
                .orElseThrow(() -> new Exception("Parking space not found with ID: " + id));
    }

    public List<ParkingSpace> getAllParkingSpaces() {
        return parkingSpaceRepository.findAll();
    }

    public List<ParkingSpace> getFacilityParkingSpaces(Long facilityId) {
        return parkingSpaceRepository.findByFacilityId(facilityId);
    }

    public List<ParkingSpace> getAvailableSpaces(Long facilityId) {
        return parkingSpaceRepository.findByFacilityIdAndStatus(facilityId, ParkingSpace.ParkingSpaceStatus.AVAILABLE);
    }

    public List<ParkingSpace> getOnlineBookableSpaces(Long facilityId) {
        return parkingSpaceRepository.findByFacilityId(facilityId).stream()
                .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE)
                .filter(s -> supportsAllocation(s.getAllocationType(), false))
                .collect(Collectors.toList());
    }

    public List<ParkingSpace> getWalkInSpaces(Long facilityId) {
        return parkingSpaceRepository.findByFacilityId(facilityId).stream()
                .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE)
                .filter(s -> supportsAllocation(s.getAllocationType(), true))
                .collect(Collectors.toList());
    }

    private boolean supportsAllocation(String allocation, boolean walkIn) {
        if (allocation == null || allocation.isBlank() || "GENERAL".equalsIgnoreCase(allocation)
                || "BOTH".equalsIgnoreCase(allocation)) {
            return true;
        }
        return walkIn ? !"ONLINE_BOOKABLE".equalsIgnoreCase(allocation)
                : !"WALKIN_ONLY".equalsIgnoreCase(allocation);
    }

    public ParkingSpace updateSpaceStatus(Long id, ParkingSpace.ParkingSpaceStatus status) throws Exception {
        ParkingSpace space = parkingSpaceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new Exception("Parking space not found with ID: " + id));
        if (status != ParkingSpace.ParkingSpaceStatus.AVAILABLE
                && status != ParkingSpace.ParkingSpaceStatus.MAINTENANCE
                && status != ParkingSpace.ParkingSpaceStatus.OUT_OF_SERVICE) {
            throw new IllegalArgumentException("Only AVAILABLE, MAINTENANCE, or OUT_OF_SERVICE are staff-controlled states.");
        }
        if (space.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED
                || space.getStatus() == ParkingSpace.ParkingSpaceStatus.RESERVED) {
            throw new IllegalStateException("Occupied or reserved spaces cannot be manually taken out of service.");
        }
        space.setStatus(status);
        if (space.getParkingType() != null) {
            String pt = space.getParkingType().trim().toUpperCase();
            if ("ELECTRIC".equals(pt)) {
                space.setParkingType("EV_CHARGING");
            } else if ("HANDICAPPED".equals(pt)) {
                space.setParkingType("ACCESSIBLE");
            } else {
                space.setParkingType(pt);
            }
        } else {
            space.setParkingType("STANDARD");
        }
        if (space.getAllocationType() == null || space.getAllocationType().trim().isEmpty()) {
            space.setAllocationType("GENERAL");
        } else {
            space.setAllocationType(space.getAllocationType().trim().toUpperCase());
        }
        if (space.getZone() == null || space.getZone().trim().isEmpty()) {
            space.setZone("ZONE-A");
        }
        if (space.getFloor() == null || space.getFloor() < 1) {
            space.setFloor(1);
        }
        if (space.getHasEVCharging() == null) {
            space.setHasEVCharging("EV_CHARGING".equalsIgnoreCase(space.getParkingType()));
        }
        if (space.getIsAccessible() == null) {
            space.setIsAccessible("ACCESSIBLE".equalsIgnoreCase(space.getParkingType()));
        }
        return parkingSpaceRepository.save(space);
    }

    public ParkingSpace toggleSpaceStatus(Long id) throws Exception {
        ParkingSpace space = getParkingSpaceById(id);
        if (space.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE) {
            space.setStatus(ParkingSpace.ParkingSpaceStatus.OCCUPIED);
        } else if (space.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED) {
            space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        } else if (space.getStatus() == ParkingSpace.ParkingSpaceStatus.MAINTENANCE) {
            space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        } else {
            space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        }
        return parkingSpaceRepository.save(space);
    }

    public List<ParkingSpace> filterSpaces(Long facilityId, Integer floor, String zone, 
                                          ParkingSpace.ParkingSpaceStatus status,
                                          String allocationType,
                                          String search) {
        List<ParkingSpace> list = (facilityId != null) ? 
                parkingSpaceRepository.findByFacilityId(facilityId) : 
                parkingSpaceRepository.findAll();

        return list.stream()
                .filter(s -> floor == null || s.getFloor().equals(floor))
                .filter(s -> zone == null || zone.isBlank() || s.getZone().equalsIgnoreCase(zone))
                .filter(s -> status == null || s.getStatus() == status)
                .filter(s -> allocationType == null || allocationType.isBlank()
                        || allocationType.trim().equalsIgnoreCase(s.getAllocationType()))
                .filter(s -> search == null || search.isBlank() || 
                             s.getSpaceNumber().toLowerCase().contains(search.toLowerCase()) ||
                             (s.getFacility() != null && s.getFacility().getName().toLowerCase().contains(search.toLowerCase())))
                .collect(Collectors.toList());
    }

    public long countByFacilityAndStatus(Long facilityId, ParkingSpace.ParkingSpaceStatus status) {
        return parkingSpaceRepository.countByFacilityIdAndStatus(facilityId, status);
    }

    /**
     * Periodic sweep (runs every 60s): releases bays that were vacated by a
     * reservation reassignment once their 10-minute grace period has elapsed,
     * making them AVAILABLE for public booking again. See
     * ParkingSpace.pendingReleaseAt and ReservationService.updateReservationSchedule.
     */
    @Scheduled(fixedRate = 60000)
    public void releaseExpiredHolds() {
        List<ParkingSpace> due = parkingSpaceRepository.findByStatusAndPendingReleaseAtBefore(
                ParkingSpace.ParkingSpaceStatus.RESERVED, LocalDateTime.now());
        for (ParkingSpace space : due) {
            space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
            space.setPendingReleaseAt(null);
            parkingSpaceRepository.save(space);
        }
    }

    public void deleteParkingSpace(Long id) throws Exception {
        ParkingSpace space = getParkingSpaceById(id);
        long reservationCount = reservationRepository.countByParkingSpaceId(id);
        if (reservationCount > 0) {
            throw new IllegalStateException("Cannot delete parking space '" + space.getSpaceNumber()
                    + "' because it has " + reservationCount
                    + " reservation record(s). Reservation history must be preserved."
                    + " Cancel or complete the related reservations before removing the space.");
        }
        parkingSpaceRepository.deleteById(id);
    }

    /**
     * Extracts a clean, standard zone identifier from strings like "ZONE-A" -> "A",
     * "ZONE-B" -> "B", "VIP-ZONE" -> "VIP", or returns "A" by default.
     */
    public static String extractZoneCode(String zone) {
        if (zone == null || zone.isBlank()) {
            return "A";
        }
        String z = zone.trim().toUpperCase();
        if (z.startsWith("ZONE-")) {
            z = z.substring(5);
        } else if (z.endsWith("-ZONE")) {
            z = z.substring(0, z.length() - 5);
        }
        z = z.replaceAll("[^A-Z0-9]", "");
        return z.isEmpty() ? "A" : z;
    }

    /**
     * Generates the next sequential space code for a given facility, floor, and zone
     * following the standard Option 1 pattern: F{facilityId}-L{floor}{zoneCode}-{slot:02d}
     * (e.g. F1-L1A-01, F1-L1A-02, F1-L2B-01).
     */
    public String generateNextSpaceNumber(Long facilityId, Integer floor, String zone) {
        if (facilityId == null) {
            return "";
        }
        int safeFloor = (floor != null && floor >= 1) ? floor : 1;
        String zoneCode = extractZoneCode(zone);
        String prefix = String.format("F%d-L%d%s-", facilityId, safeFloor, zoneCode);

        List<String> existingCodes = parkingSpaceRepository.findSpaceNumbersByFacilityIdAndPrefix(facilityId, prefix + "%");
        int maxSlot = 0;
        if (existingCodes != null) {
            for (String code : existingCodes) {
                if (code != null && code.startsWith(prefix)) {
                    String suffix = code.substring(prefix.length()).trim();
                    try {
                        int slot = Integer.parseInt(suffix);
                        if (slot > maxSlot) {
                            maxSlot = slot;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        int candidateSlot = maxSlot + 1;
        String candidateCode = String.format("%s%02d", prefix, candidateSlot);

        while (parkingSpaceRepository.findBySpaceNumber(candidateCode).isPresent()) {
            candidateSlot++;
            candidateCode = String.format("%s%02d", prefix, candidateSlot);
        }

        return candidateCode;
    }
}
