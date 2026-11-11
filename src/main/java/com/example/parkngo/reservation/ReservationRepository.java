package com.example.parkngo.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);
    @Query("SELECT r FROM Reservation r JOIN FETCH r.parkingSpace s WHERE s.facility.id = :facilityId AND r.status IN ('PENDING', 'CONFIRMED', 'VERIFIED', 'ACTIVE', 'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE')")
    List<Reservation> findLiveForFacility(@Param("facilityId") Long facilityId);
    List<Reservation> findByCustomerId(Long customerId);
    List<Reservation> findByParkingSpaceId(Long parkingSpaceId);
        long countByParkingSpaceId(Long parkingSpaceId);
    long countByParkingSpaceFacilityId(Long facilityId);

    @Query("SELECT COUNT(DISTINCT r.parkingSpace.id) FROM Reservation r WHERE r.parkingSpace.facility.id = :facilityId AND r.status IN ('PENDING', 'CONFIRMED', 'VERIFIED', 'ACTIVE', 'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE')")
    long countActiveReservationsByFacilityId(@Param("facilityId") Long facilityId);
    Optional<Reservation> findByReservationCode(String reservationCode);

    @Query("SELECT r FROM Reservation r WHERE r.status IN ('ACTIVE', 'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE') ORDER BY r.departureTime ASC")
    List<Reservation> findActiveReservations();

    @Query("SELECT r FROM Reservation r WHERE r.status IN ('PENDING', 'CONFIRMED', 'VERIFIED', 'ACTIVE', 'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE') ORDER BY r.arrivalTime DESC")
    List<Reservation> findCurrentAndUpcomingReservations();

    @Query("SELECT r FROM Reservation r WHERE r.parkingSpace.id = :spaceId " +
            "AND r.status IN ('PENDING', 'CONFIRMED', 'VERIFIED', 'ACTIVE', 'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE') " +
            "AND r.arrivalTime >= :afterTime ORDER BY r.arrivalTime ASC")
    List<Reservation> findNextReservations(@Param("spaceId") Long spaceId,
                                           @Param("afterTime") LocalDateTime afterTime);

    @Query("SELECT r FROM Reservation r WHERE r.status = 'PENDING' " +
            "AND r.paymentHoldExpiresAt IS NOT NULL " +
            "AND r.paymentHoldExpiresAt <= :now")
    List<Reservation> findExpiredPaymentHolds(@Param("now") LocalDateTime now);

    @Query("SELECT r FROM Reservation r WHERE r.parkingSpace.id = :spaceId " +
            "AND r.status IN ('PENDING', 'CONFIRMED', 'VERIFIED', 'ACTIVE', 'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE') " +
            "AND ((r.arrivalTime < :departureTime AND r.departureTime > :arrivalTime))")
    List<Reservation> findConflictingReservations(@Param("spaceId") Long spaceId,
                                                 @Param("arrivalTime") LocalDateTime arrivalTime,
                                                 @Param("departureTime") LocalDateTime departureTime);

    @Query("SELECT r FROM Reservation r WHERE r.parkingSpace.id = :spaceId " +
            "AND (:excludeReservationId IS NULL OR r.id != :excludeReservationId) " +
            "AND r.status IN ('PENDING', 'CONFIRMED', 'VERIFIED', 'ACTIVE', 'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE') " +
            "AND ((r.arrivalTime < :departureTime AND r.departureTime > :arrivalTime))")
    List<Reservation> findConflictingReservationsWithExclusion(@Param("spaceId") Long spaceId,
                                                              @Param("excludeReservationId") Long excludeReservationId,
                                                              @Param("arrivalTime") LocalDateTime arrivalTime,
                                                              @Param("departureTime") LocalDateTime departureTime);

    @Query("SELECT r FROM Reservation r WHERE r.vehicle.id = :vehicleId " +
            "AND (:excludeReservationId IS NULL OR r.id != :excludeReservationId) " +
            "AND r.status IN ('PENDING', 'CONFIRMED', 'VERIFIED', 'ACTIVE', 'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE') " +
            "AND ((r.arrivalTime < :departureTime AND r.departureTime > :arrivalTime))")
    List<Reservation> findConflictingVehicleReservations(@Param("vehicleId") Long vehicleId,
                                                        @Param("excludeReservationId") Long excludeReservationId,
                                                        @Param("arrivalTime") LocalDateTime arrivalTime,
                                                        @Param("departureTime") LocalDateTime departureTime);

    @Query("SELECT DISTINCT r.parkingSpace.id FROM Reservation r WHERE r.parkingSpace.facility.id = :facilityId " +
            "AND r.status IN ('PENDING', 'CONFIRMED', 'VERIFIED', 'ACTIVE', 'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE') " +
            "AND ((r.arrivalTime < :departureTime AND r.departureTime > :arrivalTime))")
    List<Long> findBookedSpaceIdsInFacility(@Param("facilityId") Long facilityId,
                                            @Param("arrivalTime") LocalDateTime arrivalTime,
                                            @Param("departureTime") LocalDateTime departureTime);

    @Query("SELECT r FROM Reservation r WHERE r.status = 'CONFIRMED' AND r.departureTime < :cutoff")
    List<Reservation> findStaleConfirmedReservations(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT r FROM Reservation r WHERE " +
            "LOWER(r.reservationCode) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(r.vehicle.registrationNumber) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(r.customer.fullName) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(r.parkingSpace.spaceNumber) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "STR(r.id) = :term")
    List<Reservation> searchReservations(@Param("term") String term);

    List<Reservation> findByStatus(Reservation.ReservationStatus status);

    List<Reservation> findByVehicleId(Long vehicleId);

    long countByVehicleId(Long vehicleId);

    @Query("SELECT r FROM Reservation r WHERE r.vehicle.id = :vehicleId AND r.status IN ('PENDING', 'CONFIRMED', 'VERIFIED', 'ACTIVE', 'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE')")
    List<Reservation> findActiveOrUpcomingReservationsByVehicleId(@Param("vehicleId") Long vehicleId);
}
