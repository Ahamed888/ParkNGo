package com.example.parkngo.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    @Override
    @Query("SELECT f FROM Feedback f LEFT JOIN FETCH f.customer LEFT JOIN FETCH f.reservation")
    List<Feedback> findAll();

    List<Feedback> findByCustomerId(Long customerId);
    List<Feedback> findByReservationId(Long reservationId);
    boolean existsByCustomerIdAndReservationId(Long customerId, Long reservationId);
    List<Feedback> findByStatus(Feedback.FeedbackStatus status);
}
