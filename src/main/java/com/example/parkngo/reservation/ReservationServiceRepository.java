package com.example.parkngo.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationServiceRepository extends JpaRepository<ReservationServiceItem, Long> {
    List<ReservationServiceItem> findByReservationId(Long reservationId);
    long countByServiceId(Long serviceId);
}
