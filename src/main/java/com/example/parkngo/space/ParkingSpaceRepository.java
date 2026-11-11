package com.example.parkngo.space;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSpaceRepository extends JpaRepository<ParkingSpace, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ParkingSpace s WHERE s.id = :id")
    Optional<ParkingSpace> findByIdForUpdate(@Param("id") Long id);

    Optional<ParkingSpace> findBySpaceNumber(String spaceNumber);
    /** Bays whose post-reassignment grace period has elapsed - see ParkingSpaceService's scheduled sweep. */
    List<ParkingSpace> findByStatusAndPendingReleaseAtBefore(ParkingSpace.ParkingSpaceStatus status, LocalDateTime time);
    Optional<ParkingSpace> findBySpaceNumberAndIdNot(String spaceNumber, Long id);
    List<ParkingSpace> findByFacilityId(Long facilityId);
    List<ParkingSpace> findByFacilityIdAndStatus(Long facilityId, ParkingSpace.ParkingSpaceStatus status);
    List<ParkingSpace> findByFacilityIdAndFloor(Long facilityId, Integer floor);
    List<ParkingSpace> findByFacilityIdAndZone(Long facilityId, String zone);
    List<ParkingSpace> findByFacilityIdAndAllocationType(Long facilityId, String allocationType);
    List<ParkingSpace> findByStatus(ParkingSpace.ParkingSpaceStatus status);
    long countByFacilityId(Long facilityId);
    long countByFacilityIdAndStatus(Long facilityId, ParkingSpace.ParkingSpaceStatus status);
    long countByFacilityIdAndHasEVChargingTrue(Long facilityId);
    long countByFacilityIdAndIsAccessibleTrue(Long facilityId);

    @Query("SELECT MAX(s.floor) FROM ParkingSpace s WHERE s.facility.id = :facilityId")
    Integer findMaxFloorByFacilityId(@Param("facilityId") Long facilityId);

    @Query("SELECT s.spaceNumber FROM ParkingSpace s WHERE s.facility.id = :facilityId AND s.spaceNumber LIKE :prefixPattern")
    List<String> findSpaceNumbersByFacilityIdAndPrefix(@Param("facilityId") Long facilityId, @Param("prefixPattern") String prefixPattern);
}
