package com.example.parkngo.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalkInReservationRepository extends JpaRepository<WalkInReservation, Long> {
    List<WalkInReservation> findByGuestVehicleRegNumber(String guestVehicleRegNumber);
}
