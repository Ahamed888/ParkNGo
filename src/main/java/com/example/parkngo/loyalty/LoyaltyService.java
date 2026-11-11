package com.example.parkngo.loyalty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * LoyaltyService.java
 * "Loyalty-lite" tier tracking: a customer's completedReservationCount only
 * ever advances when {@link #onReservationCompleted(Long)} is called. There
 * is no other trigger and no derived/backfill computation from any other
 * table - this is not automatic. Reservation Management (a teammate's
 * module) is expected to call it when a reservation's status transitions to
 * COMPLETED; until whoever owns that module wires that call in, this
 * counter simply never advances for real bookings. See the module's
 * integration note. In the meantime this class is fully exercised by its
 * own unit tests, and is called by nothing else in this codebase.
 */
@Service
@Transactional
public class LoyaltyService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);

    static final int GOLD_TIER_THRESHOLD = 10;
    static final BigDecimal GOLD_DISCOUNT_RATE = BigDecimal.valueOf(0.10);

    private final LoyaltyStatusRepository loyaltyStatusRepository;

    public LoyaltyService(LoyaltyStatusRepository loyaltyStatusRepository) {
        this.loyaltyStatusRepository = loyaltyStatusRepository;
    }

    /**
     * MUST be called by Reservation Management whenever a reservation's
     * status transitions to COMPLETED (e.g. on vehicle exit). Not automatic.
     */
    public LoyaltyStatus onReservationCompleted(Long customerId) {
        LoyaltyStatus status = getOrCreateLoyaltyStatus(customerId);
        status.setCompletedReservationCount(status.getCompletedReservationCount() + 1);
        if (status.getCompletedReservationCount() >= GOLD_TIER_THRESHOLD && status.getTier() != LoyaltyStatus.LoyaltyTier.GOLD) {
            status.setTier(LoyaltyStatus.LoyaltyTier.GOLD);
            log.info("Customer {} unlocked GOLD loyalty tier after {} completed reservations", customerId, status.getCompletedReservationCount());
        }
        return loyaltyStatusRepository.save(status);
    }

    /** The discount rate (0 to 1) the customer's current tier earns at checkout - 0 for STANDARD. */
    public BigDecimal getLoyaltyDiscountRate(Long customerId) {
        LoyaltyStatus status = getOrCreateLoyaltyStatus(customerId);
        return status.getTier() == LoyaltyStatus.LoyaltyTier.GOLD ? GOLD_DISCOUNT_RATE : BigDecimal.ZERO;
    }

    /** Read-only view for display (e.g. "My Passes" / a loyalty progress widget). */
    public LoyaltyStatus getLoyaltyStatus(Long customerId) {
        return getOrCreateLoyaltyStatus(customerId);
    }

    private LoyaltyStatus getOrCreateLoyaltyStatus(Long customerId) {
        return loyaltyStatusRepository.findById(customerId)
                .orElseGet(() -> new LoyaltyStatus(customerId));
    }
}
