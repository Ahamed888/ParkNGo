package com.example.parkngo.facility;

import com.example.parkngo.reservation.ReservationServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// [Service Layer]: Encapsulates amenity management logic, status transitions, and ensures transactional boundaries.
@Service
@Transactional
public class FacilityServiceService {
    private final FacilityServiceRepository serviceRepository;
    private final ReservationServiceRepository reservationServiceRepository;
    private final ParkingFacilityRepository facilityRepository;

    public FacilityServiceService(FacilityServiceRepository serviceRepository,
                                  ReservationServiceRepository reservationServiceRepository,
                                  ParkingFacilityRepository facilityRepository) {
        this.serviceRepository = serviceRepository;
        this.reservationServiceRepository = reservationServiceRepository;
        this.facilityRepository = facilityRepository;
    }

    // [ Uniqueness Validation]: Verifies facility-scoped name uniqueness before persisting new amenity services.
    public FacilityService createService(FacilityService service) throws Exception {
        return prepareAndSaveNewService(service, service.getFacilities());
    }

    public FacilityService createService(FacilityService service, Collection<Long> facilityIds) throws Exception {
        Set<ParkingFacility> facilities = resolveFacilities(facilityIds, Set.of());
        return prepareAndSaveNewService(service, facilities);
    }

    private FacilityService prepareAndSaveNewService(FacilityService service,
                                                     Collection<ParkingFacility> facilities) {
        if (service.getName() == null || service.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Service name is required");
        }
        String name = service.getName().trim();
        service.setName(name);

        if (service.getDescription() != null) {
            service.setDescription(service.getDescription().trim());
        }

        if (facilities == null || facilities.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one active parking complex.");
        }
        for (ParkingFacility facility : facilities) {
            if (facility.getId() != null && serviceRepository.findByFacilityIdAndNameIgnoreCase(facility.getId(), name).isPresent()) {
                throw new IllegalArgumentException("A service with the name '" + name + "' already exists for this facility.");
            }
        }
        service.setFacilities(facilities);

        if (service.getStatus() == null) {
            service.setStatus(FacilityService.ServiceStatus.AVAILABLE);
        }

        return serviceRepository.save(service);
    }

    public FacilityService getServiceById(Long id) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("Service ID is required");
        }
        return serviceRepository.findById(id)
                .filter(service -> !service.isDeleted())
                .orElseThrow(() -> new Exception("Service not found"));
    }

    public List<FacilityService> getAllServices() {
        try {
            return serviceRepository.findAll().stream().filter(service -> !service.isDeleted()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<FacilityService> getFacilityServices(Long facilityId) {
        if (facilityId == null) return List.of();
        try {
            return serviceRepository.findByFacilityId(facilityId).stream().filter(service -> !service.isDeleted()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    // [Service Availability Logic]: Returns only AVAILABLE services for a complex; inactive amenities are filtered out.
    public List<FacilityService> getAvailableServices(Long facilityId) {
        if (facilityId == null) return List.of();
        try {
            List<FacilityService> list = serviceRepository.findByFacilityIdAndStatus(facilityId, FacilityService.ServiceStatus.AVAILABLE);
            if (list != null && !list.isEmpty()) {
                return list.stream().filter(FacilityService::isAvailable).toList();
            }
            return serviceRepository.findByFacilityId(facilityId).stream()
                    .filter(FacilityService::isAvailable)
                    .toList();
        } catch (Exception e) {
            try {
                return serviceRepository.findByFacilityId(facilityId).stream()
                        .filter(FacilityService::isAvailable)
                        .toList();
            } catch (Exception ex) {
                return List.of();
            }
        }
    }

    public FacilityService updateService(FacilityService service) throws Exception {
        if (service.getId() == null) {
            throw new IllegalArgumentException("Service ID must not be null for update");
        }
        return updateService(service.getId(), service, service.getFacility());
    }

    public FacilityService updateService(Long id, FacilityService service, ParkingFacility facility) throws Exception {
        Collection<ParkingFacility> target = facility == null ? getServiceById(id).getFacilities() : List.of(facility);
        return updateServiceEntity(id, service, target);
    }

    public FacilityService updateService(Long id, FacilityService service, Collection<Long> facilityIds) throws Exception {
        FacilityService existing = getServiceById(id);
        Set<Long> existingIds = existing.getFacilities().stream()
                .map(ParkingFacility::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<ParkingFacility> submitted = facilityIds == null || facilityIds.isEmpty()
                ? new LinkedHashSet<>()
                : resolveFacilities(facilityIds, existingIds);

        // Inactive existing assignments are locked on the form and survive ordinary edits.
        existing.getFacilities().stream()
                .filter(f -> f.getStatus() == ParkingFacility.FacilityStatus.INACTIVE)
                .forEach(submitted::add);
        return updateServiceEntity(id, service, submitted);
    }

    private FacilityService updateServiceEntity(Long id, FacilityService service,
                                                Collection<ParkingFacility> facilities) throws Exception {
        FacilityService existing = getServiceById(id);
        String name = service.getName() != null ? service.getName().trim() : existing.getName();

        if (facilities == null || facilities.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one active parking complex.");
        }
        for (ParkingFacility facility : facilities) {
            if (facility.getId() != null && serviceRepository.findByFacilityIdAndNameIgnoreCaseAndIdNot(facility.getId(), name, id).isPresent()) {
                throw new IllegalArgumentException("A service with the name '" + name + "' already exists for this facility.");
            }
        }

        existing.setName(name);
        if (service.getDescription() != null) {
            existing.setDescription(service.getDescription().trim());
        }
        existing.setPrice(service.getPrice());
        existing.setFacilities(facilities);
        if (service.getStatus() != null) {
            existing.setStatus(service.getStatus());
        }
        return serviceRepository.save(existing);
    }

    private Set<ParkingFacility> resolveFacilities(Collection<Long> facilityIds, Set<Long> existingIds) {
        if (facilityIds == null || facilityIds.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one active parking complex.");
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long facilityId : facilityIds) {
            if (facilityId == null) {
                throw new IllegalArgumentException("One or more selected parking complexes are invalid.");
            }
            uniqueIds.add(facilityId);
        }

        List<ParkingFacility> found = facilityRepository.findAllById(uniqueIds);
        Map<Long, ParkingFacility> byId = new LinkedHashMap<>();
        found.forEach(facility -> byId.put(facility.getId(), facility));
        if (byId.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("One or more selected parking complexes do not exist.");
        }

        LinkedHashSet<ParkingFacility> resolved = new LinkedHashSet<>();
        for (Long facilityId : uniqueIds) {
            ParkingFacility facility = byId.get(facilityId);
            if (facility.getStatus() != ParkingFacility.FacilityStatus.ACTIVE && !existingIds.contains(facilityId)) {
                throw new IllegalArgumentException("Inactive parking complex '" + facility.getName() + "' cannot be newly assigned.");
            }
            resolved.add(facility);
        }
        return resolved;
    }

    public FacilityService toggleServiceStatus(Long id) throws Exception {
        FacilityService service = getServiceById(id);
        if (service.getStatus() == FacilityService.ServiceStatus.AVAILABLE) {
            service.setStatus(FacilityService.ServiceStatus.UNAVAILABLE);
        } else {
            service.setStatus(FacilityService.ServiceStatus.AVAILABLE);
        }
        return serviceRepository.save(service);
    }

    public void deactivateService(Long id) throws Exception {
        FacilityService service = getServiceById(id);
        service.setStatus(FacilityService.ServiceStatus.UNAVAILABLE);
        serviceRepository.save(service);
    }

    public void activateService(Long id) throws Exception {
        FacilityService service = getServiceById(id);
        service.setStatus(FacilityService.ServiceStatus.AVAILABLE);
        serviceRepository.save(service);
    }

    /**
     * Remove from the catalog while preserving reservation and membership references.
     * The method name is retained for existing admin routes.
     */
    public void hardDeleteService(Long id) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("Service ID is required");
        }
        FacilityService service = serviceRepository.findById(id)
                .orElseThrow(() -> new Exception("Service not found"));
        service.setDeleted(true);
        service.setStatus(FacilityService.ServiceStatus.UNAVAILABLE);
        serviceRepository.saveAndFlush(service);
    }

    public long countServices() {
        return getAllServices().size();
    }

    public long countAvailableServices() {
        return serviceRepository.findByStatus(FacilityService.ServiceStatus.AVAILABLE).stream().filter(FacilityService::isAvailable).count();
    }
}

