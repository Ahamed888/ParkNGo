package com.example.parkngo.loyalty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LoyaltyServiceTest.java
 * Pure Mockito unit tests (no Spring context, no database) for the
 * "loyalty-lite" tier system: the counter only advances through
 * onReservationCompleted, GOLD unlocks at exactly 10 completed
 * reservations, and the discount rate read is 0 for STANDARD / 10% for
 * GOLD. A brand new customer (no row yet) is treated as STANDARD with 0
 * completed reservations rather than throwing.
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {

    @Mock
    private LoyaltyStatusRepository loyaltyStatusRepository;

    @InjectMocks
    private LoyaltyService loyaltyService;

    @Test
    void getLoyaltyStatus_newCustomer_defaultsToStandardWithZeroCount() {
        when(loyaltyStatusRepository.findById(1L)).thenReturn(Optional.empty());

        LoyaltyStatus status = loyaltyService.getLoyaltyStatus(1L);

        assertEquals(LoyaltyStatus.LoyaltyTier.STANDARD, status.getTier());
        assertEquals(0, status.getCompletedReservationCount());
    }

    @Test
    void onReservationCompleted_incrementsCountAndPersists() {
        when(loyaltyStatusRepository.findById(1L)).thenReturn(Optional.empty());
        when(loyaltyStatusRepository.save(any(LoyaltyStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        LoyaltyStatus status = loyaltyService.onReservationCompleted(1L);

        assertEquals(1, status.getCompletedReservationCount());
        assertEquals(LoyaltyStatus.LoyaltyTier.STANDARD, status.getTier());
        verify(loyaltyStatusRepository).save(any(LoyaltyStatus.class));
    }

    @Test
    void onReservationCompleted_unlocksGoldAtTenCompletedReservations() {
        LoyaltyStatus existing = new LoyaltyStatus(1L);
        existing.setCompletedReservationCount(9);
        when(loyaltyStatusRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(loyaltyStatusRepository.save(any(LoyaltyStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        LoyaltyStatus status = loyaltyService.onReservationCompleted(1L);

        assertEquals(10, status.getCompletedReservationCount());
        assertEquals(LoyaltyStatus.LoyaltyTier.GOLD, status.getTier());
    }

    @Test
    void onReservationCompleted_doesNotDowngradeAnAlreadyGoldCustomer() {
        LoyaltyStatus existing = new LoyaltyStatus(1L);
        existing.setCompletedReservationCount(15);
        existing.setTier(LoyaltyStatus.LoyaltyTier.GOLD);
        when(loyaltyStatusRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(loyaltyStatusRepository.save(any(LoyaltyStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        LoyaltyStatus status = loyaltyService.onReservationCompleted(1L);

        assertEquals(16, status.getCompletedReservationCount());
        assertEquals(LoyaltyStatus.LoyaltyTier.GOLD, status.getTier());
    }

    @Test
    void getLoyaltyDiscountRate_standardTier_isZero() {
        when(loyaltyStatusRepository.findById(2L)).thenReturn(Optional.empty());

        BigDecimal rate = loyaltyService.getLoyaltyDiscountRate(2L);

        assertEquals(0, BigDecimal.ZERO.compareTo(rate));
    }

    @Test
    void getLoyaltyDiscountRate_goldTier_isTenPercent() {
        LoyaltyStatus gold = new LoyaltyStatus(3L);
        gold.setTier(LoyaltyStatus.LoyaltyTier.GOLD);
        when(loyaltyStatusRepository.findById(3L)).thenReturn(Optional.of(gold));

        BigDecimal rate = loyaltyService.getLoyaltyDiscountRate(3L);

        assertEquals(0, BigDecimal.valueOf(0.10).compareTo(rate));
    }
}
