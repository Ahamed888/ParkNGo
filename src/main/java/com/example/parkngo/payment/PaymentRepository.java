package com.example.parkngo.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByReservationId(Long reservationId);

    List<Payment> findByReservationIdAndRemarks(Long reservationId, String remarks);

    List<Payment> findBySubscriptionId(Long subscriptionId);

    Optional<Payment> findByTransactionCode(String transactionCode);

    boolean existsByTransactionCode(String transactionCode);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Payment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Payment> findAllByOrderByCreatedAtDesc();

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
