package com.example.parkngo.discount;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, Long> {
    Optional<Discount> findByCode(String code);
    List<Discount> findByStatus(Discount.DiscountStatus status);
    boolean existsByCode(String code);

    /**
     * Row-locking lookup used by the checkout module when placing a hold on
     * a discount code: the PESSIMISTIC_WRITE lock serializes concurrent
     * attempts to redeem the same code, so the global/per-customer cap
     * checks and the uses_count increment that follow this call cannot race
     * against another transaction doing the same thing. Must be called
     * inside an existing @Transactional method - the lock is released when
     * that transaction commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Discount d WHERE d.code = :code")
    Optional<Discount> findByCodeForUpdate(String code);
}
