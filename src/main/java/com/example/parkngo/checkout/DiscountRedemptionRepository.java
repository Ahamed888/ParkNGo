package com.example.parkngo.checkout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface DiscountRedemptionRepository extends JpaRepository<DiscountRedemption, Long> {

    /**
     * Counts this customer's HELD-or-CONFIRMED redemptions of one discount
     * code - checked against the per-customer cap at hold time, not just at
     * commit time, so two uncommitted holds by the same customer for a
     * one-per-customer code can't both later succeed.
     */
    long countByDiscountIdAndCustomerIdAndStatusIn(Long discountId, Long customerId, Collection<DiscountRedemption.RedemptionStatus> statuses);

    Optional<DiscountRedemption> findByQuoteId(Long quoteId);

    void deleteByQuoteIdAndStatus(Long quoteId, DiscountRedemption.RedemptionStatus status);
}
