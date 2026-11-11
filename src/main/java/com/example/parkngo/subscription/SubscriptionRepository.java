package com.example.parkngo.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByCustomer_IdOrderByIdDesc(Long customerId);
    List<Subscription> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    List<Subscription> findByStatus(Subscription.SubscriptionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.id = :id")
    Optional<Subscription> findByIdForUpdate(@Param("id") Long id);

    Optional<Subscription> findByGateAccessPin(String gateAccessPin);

    boolean existsByGateAccessPin(String gateAccessPin);

    /**
     * The customer's next-to-expire ACTIVE pass, used by the checkout
     * module as the hold candidate - matches the "use the one expiring
     * soonest first" ordering from the design.
     */
    Optional<Subscription> findFirstByCustomer_IdAndStatusOrderByEndDateAsc(Long customerId, Subscription.SubscriptionStatus status);

    /**
     * Used at purchase time to block a customer from buying a second, active
     * pass of the exact same package while one they already own hasn't
     * expired/been cancelled yet - an EXHAUSTED (visits used up) or EXPIRED
     * pass never matches this, so re-buying once a pass genuinely runs out
     * still works.
     */
    List<Subscription> findByCustomer_IdAndMembershipPackage_IdAndStatus(Long customerId, Long packageId, Subscription.SubscriptionStatus status);

    boolean existsByMembershipPackage_Id(Long packageId);

    long countByMembershipPackage_Id(Long packageId);

    /**
     * Atomically consumes one visit from a visit-limited pass, or is a no-op
     * (matches, but changes nothing) for an unlimited-within-duration pass
     * (visitsRemaining IS NULL). Returns the number of rows updated - the
     * checkout module treats 0 as "someone else already holds the last
     * visit" rather than throwing, since this is a single conditional
     * UPDATE, not a read-then-write, so it is race-safe under concurrent
     * holds against the same pass.
     */
    @Modifying
    @Query("UPDATE Subscription s SET " +
            "s.visitsRemaining = CASE WHEN s.visitsRemaining IS NULL THEN NULL ELSE s.visitsRemaining - 1 END, " +
            "s.status = CASE WHEN s.visitsRemaining IS NOT NULL AND s.visitsRemaining - 1 <= 0 THEN com.example.parkngo.subscription.Subscription.SubscriptionStatus.EXHAUSTED ELSE s.status END " +
            "WHERE s.id = :id AND s.status = com.example.parkngo.subscription.Subscription.SubscriptionStatus.ACTIVE " +
            "AND (s.visitsRemaining IS NULL OR s.visitsRemaining > 0)")
    int tryDecrementVisit(@Param("id") Long id);

    /**
     * Gives back exactly one visit that a hold previously consumed (used on
     * quote expiry/release and on refund rollback). No-op for an unlimited
     * pass. Also flips a pass that was flipped to EXHAUSTED by the matching
     * decrement back to ACTIVE - a pass that later goes past its own
     * expiry date is corrected back to EXPIRED the next time it is read
     * (see Subscription's lazy expiry check), so this is safe even though
     * it does not itself re-check the expiry date.
     */
    @Modifying
    @Query("UPDATE Subscription s SET " +
            "s.visitsRemaining = CASE WHEN s.visitsRemaining IS NULL THEN NULL ELSE s.visitsRemaining + 1 END, " +
            "s.status = CASE WHEN s.status = com.example.parkngo.subscription.Subscription.SubscriptionStatus.EXHAUSTED THEN com.example.parkngo.subscription.Subscription.SubscriptionStatus.ACTIVE ELSE s.status END " +
            "WHERE s.id = :id")
    void restoreVisit(@Param("id") Long id);
}
