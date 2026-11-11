package com.example.parkngo.facility;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// [Repository Layer]: Abstracts database operations for amenity services away from business and web layers.
@Repository
public interface FacilityServiceRepository extends JpaRepository<FacilityService, Long> {
    @Query("select distinct s from FacilityService s join s.facilities f where s.deleted = false and f.id = :facilityId")
    List<FacilityService> findByFacilityId(@Param("facilityId") Long facilityId);

    // [ Filtered Service Query]: Fetches only AVAILABLE services mapped to a facility during customer reservation.
    @Query("select distinct s from FacilityService s join s.facilities f where s.deleted = false and f.id = :facilityId and s.status = :status")
    List<FacilityService> findByFacilityIdAndStatus(@Param("facilityId") Long facilityId,
                                                    @Param("status") FacilityService.ServiceStatus status);

    @Query("select count(distinct s.id) from FacilityService s join s.facilities f where s.deleted = false and f.id = :facilityId")
    long countByFacilityId(@Param("facilityId") Long facilityId);
    List<FacilityService> findByStatus(FacilityService.ServiceStatus status);

    @Query("select distinct s from FacilityService s join s.facilities f where s.deleted = false and f.id = :facilityId and lower(s.name) = lower(:name)")
    Optional<FacilityService> findByFacilityIdAndNameIgnoreCase(@Param("facilityId") Long facilityId,
                                                                @Param("name") String name);

    @Query("select distinct s from FacilityService s join s.facilities f where s.deleted = false and f.id = :facilityId and lower(s.name) = lower(:name) and s.id <> :id")
    Optional<FacilityService> findByFacilityIdAndNameIgnoreCaseAndIdNot(@Param("facilityId") Long facilityId,
                                                                        @Param("name") String name,
                                                                        @Param("id") Long id);
}
