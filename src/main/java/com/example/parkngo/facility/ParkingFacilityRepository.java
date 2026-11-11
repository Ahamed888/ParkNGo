package com.example.parkngo.facility;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// [Data Access Layer (Repository Pattern)]: Extends JpaRepository to provide CRUD operations and abstract SQL queries.
@Repository
public interface ParkingFacilityRepository extends JpaRepository<ParkingFacility, Long> {
    Optional<ParkingFacility> findByNameIgnoreCase(String name);
    Optional<ParkingFacility> findByNameIgnoreCaseAndIdNot(String name, Long id);

    // [VIVA - Derived Query Method]: Spring Data JPA generates SQL automatically from method name to filter by facility status.
    List<ParkingFacility> findByStatus(ParkingFacility.FacilityStatus status);
    List<ParkingFacility> findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(String name, String address);
    List<ParkingFacility> findByNameContainingIgnoreCaseAndStatusOrAddressContainingIgnoreCaseAndStatus(String name, ParkingFacility.FacilityStatus s1, String address, ParkingFacility.FacilityStatus s2);
}

