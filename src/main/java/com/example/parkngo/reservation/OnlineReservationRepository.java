package com.example.parkngo.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OnlineReservationRepository extends JpaRepository<OnlineReservation, Long> {
    Optional<OnlineReservation> findByAdvanceBookingRef(String advanceBookingRef);
}
