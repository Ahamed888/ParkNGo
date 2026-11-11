package com.example.parkngo.checkout;

import com.example.parkngo.checkout.exception.QuoteExpiredException;
import com.example.parkngo.checkout.exception.QuoteMismatchException;
import com.example.parkngo.discount.Discount;
import com.example.parkngo.discount.DiscountRepository;
import com.example.parkngo.discount.exception.ResourceNotFoundException;
import com.example.parkngo.subscription.Subscription;
import com.example.parkngo.subscription.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CheckoutServiceTest.java
 * Pure Mockito unit tests (no Spring context, no database) covering the
 * hold-at-preview / finalize-at-commit / release-on-expiry pattern: package
 * compares package and promo discounts; an exhausted/capped resource falls through
 * rather than failing preview outright, commit is idempotent for the SAME
 * order and rejects a DIFFERENT order reusing the same quote, an expired
 * quote is refused, and rollback gives back exactly what a commit consumed.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private CheckoutQuoteRepository quoteRepository;
    @Mock
    private DiscountRedemptionRepository redemptionRepository;
    @Mock
    private DiscountRepository discountRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private CheckoutService checkoutService;

    /** Every quote saved gets an auto-incrementing id, mimicking JPA identity generation. */
    private void stubQuoteSaveAssignsId() {
        AtomicLong nextId = new AtomicLong(1);
        when(quoteRepository.save(any(CheckoutQuote.class))).thenAnswer(inv -> {
            CheckoutQuote q = inv.getArgument(0);
            if (q.getId() == null) {
                q.setId(nextId.getAndIncrement());
            }
            return q;
        });
    }

    private Subscription activePackage(Long id, Integer visitsRemaining) {
        Subscription sub = new Subscription();
        sub.setId(id);
        sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        sub.setEndDate(LocalDate.now().plusDays(10));
        sub.setVisitsRemaining(visitsRemaining);
        return sub;
    }

    private Discount activeDiscount(Long id, String code, BigDecimal percentValue) {
        Discount d = new Discount();
        d.setId(id);
        d.setCode(code);
        d.setDiscountType(Discount.DiscountType.PERCENTAGE);
        d.setDiscountValue(percentValue);
        d.setValidFromDate(LocalDate.now().minusDays(1));
        d.setValidToDate(LocalDate.now().plusDays(1));
        d.setStatus(Discount.DiscountStatus.ACTIVE);
        d.setAppliesTo(Discount.AppliesTo.ALL);
        d.setMaxUses(null);
        d.setMaxUsesPerCustomer(1);
        d.setUsesCount(0);
        return d;
    }

    // --- Preview: package and promo discounts ---

    @Test
    void facilityPaymentUsesConfiguredParkingBenefitWhenLegacyDiscountIsZero() {
        stubQuoteSaveAssignsId();
        Subscription subscription = activePackage(50L, null);
        com.example.parkngo.discount.Package pkg = new com.example.parkngo.discount.Package();
        var benefit = new com.example.parkngo.discount.MembershipBenefit();
        benefit.setType(com.example.parkngo.discount.MembershipBenefit.BenefitType.PARKING_DISCOUNT);
        benefit.setValueAmount(BigDecimal.valueOf(25));
        pkg.setBenefits(java.util.Set.of(benefit));
        subscription.setMembershipPackage(pkg);
        when(subscriptionRepository.findByCustomer_IdOrderByIdDesc(1L)).thenReturn(List.of(subscription));
        when(subscriptionRepository.tryDecrementVisit(50L)).thenReturn(1);

        CheckoutQuote quote = checkoutService.previewCheckoutForFacility(1L, BigDecimal.valueOf(1000), null, LocalDateTime.now(), 7L);

        assertEquals(0, BigDecimal.valueOf(750).compareTo(quote.getFinalAmount()));
        assertEquals(50L, quote.getAppliedSubscriptionId());
        verify(subscriptionRepository, times(1)).tryDecrementVisit(50L);
    }

    @Test
    void previewCheckout_appliesConfiguredPackageDiscountAndSkipsPromo() {
        stubQuoteSaveAssignsId();
        when(quoteRepository.findByCustomerIdAndStatusAndExpiresAtBefore(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        Subscription subscription = activePackage(50L, 3);
        com.example.parkngo.discount.Package membershipPackage = new com.example.parkngo.discount.Package();
        membershipPackage.setDiscountPercentage(BigDecimal.valueOf(20));
        subscription.setMembershipPackage(membershipPackage);
        when(subscriptionRepository.findFirstByCustomer_IdAndStatusOrderByEndDateAsc(1L, Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.tryDecrementVisit(50L)).thenReturn(1);

        CheckoutQuote quote = checkoutService.previewCheckout(1L, BigDecimal.valueOf(500), null, LocalDateTime.now());

        assertEquals(50L, quote.getAppliedSubscriptionId());
        assertNull(quote.getAppliedDiscountId());
        assertEquals(0, BigDecimal.valueOf(400).compareTo(quote.getFinalAmount()));
        verifyNoInteractions(discountRepository);
    }

    @Test
    void previewCheckout_packageHoldFails_fallsThroughToDiscountCode() {
        stubQuoteSaveAssignsId();
        when(quoteRepository.findByCustomerIdAndStatusAndExpiresAtBefore(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(subscriptionRepository.findFirstByCustomer_IdAndStatusOrderByEndDateAsc(1L, Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activePackage(50L, 1)));
        // Someone else (or an earlier open quote) already holds the last visit.
        when(subscriptionRepository.tryDecrementVisit(50L)).thenReturn(0);

        Discount discount = activeDiscount(77L, "SAVE10", BigDecimal.TEN);
        when(discountRepository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(discount));
        when(redemptionRepository.countByDiscountIdAndCustomerIdAndStatusIn(eq(77L), eq(1L), anyCollection())).thenReturn(0L);

        CheckoutQuote quote = checkoutService.previewCheckout(1L, BigDecimal.valueOf(500), "save10", LocalDateTime.now());

        assertNull(quote.getAppliedSubscriptionId());
        assertEquals(77L, quote.getAppliedDiscountId());
        assertEquals(0, BigDecimal.valueOf(450).compareTo(quote.getFinalAmount()));
        verify(redemptionRepository).save(any(DiscountRedemption.class));
        verify(discountRepository).save(discount);
        assertEquals(1, discount.getUsesCount());
    }

    @Test
    void previewCheckoutForFacility_doesNotUsePassOutsideItsComplexes() {
        stubQuoteSaveAssignsId();
        when(quoteRepository.findByCustomerIdAndStatusAndExpiresAtBefore(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        Subscription subscription = activePackage(50L, 3);
        com.example.parkngo.discount.Package membershipPackage = new com.example.parkngo.discount.Package();
        membershipPackage.setAllFacilities(false);
        membershipPackage.setDiscountPercentage(BigDecimal.valueOf(20));
        subscription.setMembershipPackage(membershipPackage);
        when(subscriptionRepository.findByCustomer_IdOrderByIdDesc(1L)).thenReturn(List.of(subscription));

        CheckoutQuote quote = checkoutService.previewCheckoutForFacility(
                1L, BigDecimal.valueOf(500), null, LocalDateTime.now(), 9L);

        assertNull(quote.getAppliedSubscriptionId());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(quote.getFinalAmount()));
        verify(subscriptionRepository, never()).tryDecrementVisit(anyLong());
    }

    @Test
    void previewPackagePurchase_neverHoldsAPackage_evenIfOneIsActive() {
        stubQuoteSaveAssignsId();

        CheckoutQuote quote = checkoutService.previewPackagePurchase(1L, BigDecimal.valueOf(1000), null, LocalDateTime.now());

        assertNull(quote.getAppliedSubscriptionId());
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(quote.getFinalAmount()));
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void previewCheckout_discountAtPerCustomerCap_fallsThroughToFullPrice() {
        stubQuoteSaveAssignsId();
        when(quoteRepository.findByCustomerIdAndStatusAndExpiresAtBefore(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(subscriptionRepository.findFirstByCustomer_IdAndStatusOrderByEndDateAsc(1L, Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        Discount discount = activeDiscount(77L, "ONEPERCUST", BigDecimal.TEN);
        when(discountRepository.findByCodeForUpdate("ONEPERCUST")).thenReturn(Optional.of(discount));
        // This customer already holds/confirmed one redemption of this one-per-customer code.
        when(redemptionRepository.countByDiscountIdAndCustomerIdAndStatusIn(eq(77L), eq(1L), anyCollection())).thenReturn(1L);

        CheckoutQuote quote = checkoutService.previewCheckout(1L, BigDecimal.valueOf(500), "oneperCust", LocalDateTime.now());

        assertNull(quote.getAppliedDiscountId());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(quote.getFinalAmount()));
        verify(redemptionRepository, never()).save(any(DiscountRedemption.class));
        verify(discountRepository, never()).save(any(Discount.class));
    }

    // --- Commit: finalize, idempotency, mismatch, expiry ---

    @Test
    void commitCheckout_finalizesQuoteAndConfirmsRedemption() {
        CheckoutQuote quote = new CheckoutQuote();
        quote.setId(10L);
        quote.setCustomerId(1L);
        quote.setOriginalAmount(BigDecimal.valueOf(500));
        quote.setFinalAmount(BigDecimal.valueOf(450));
        quote.setAppliedDiscountId(77L);
        quote.setExpiresAt(LocalDateTime.now().plusMinutes(4));
        quote.setStatus(CheckoutQuote.QuoteStatus.OPEN);

        DiscountRedemption redemption = new DiscountRedemption();
        redemption.setId(900L);
        redemption.setDiscountId(77L);
        redemption.setCustomerId(1L);
        redemption.setQuoteId(10L);
        redemption.setStatus(DiscountRedemption.RedemptionStatus.HELD);

        when(quoteRepository.findById(10L)).thenReturn(Optional.of(quote));
        when(redemptionRepository.findByQuoteId(10L)).thenReturn(Optional.of(redemption));
        when(quoteRepository.save(any(CheckoutQuote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(redemptionRepository.save(any(DiscountRedemption.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutQuote result = checkoutService.commitCheckout(200L, 10L);

        assertEquals(CheckoutQuote.QuoteStatus.CONSUMED, result.getStatus());
        assertEquals(200L, result.getConsumedByOrderReferenceId());

        ArgumentCaptor<DiscountRedemption> captor = ArgumentCaptor.forClass(DiscountRedemption.class);
        verify(redemptionRepository).save(captor.capture());
        assertEquals(DiscountRedemption.RedemptionStatus.CONFIRMED, captor.getValue().getStatus());
        assertEquals(200L, captor.getValue().getOrderReferenceId());
    }

    @Test
    void commitCheckout_retriedWithSameOrder_isIdempotentNoOp() {
        CheckoutQuote quote = new CheckoutQuote();
        quote.setId(10L);
        quote.setStatus(CheckoutQuote.QuoteStatus.CONSUMED);
        quote.setConsumedByOrderReferenceId(200L);
        quote.setExpiresAt(LocalDateTime.now().plusMinutes(4));

        when(quoteRepository.findById(10L)).thenReturn(Optional.of(quote));

        CheckoutQuote result = checkoutService.commitCheckout(200L, 10L);

        assertEquals(CheckoutQuote.QuoteStatus.CONSUMED, result.getStatus());
        verifyNoInteractions(redemptionRepository);
        verify(quoteRepository, never()).save(any());
    }

    @Test
    void commitCheckout_reusedAgainstADifferentOrder_throwsMismatch() {
        CheckoutQuote quote = new CheckoutQuote();
        quote.setId(10L);
        quote.setStatus(CheckoutQuote.QuoteStatus.CONSUMED);
        quote.setConsumedByOrderReferenceId(200L);
        quote.setExpiresAt(LocalDateTime.now().plusMinutes(4));

        when(quoteRepository.findById(10L)).thenReturn(Optional.of(quote));

        assertThrows(QuoteMismatchException.class, () -> checkoutService.commitCheckout(999L, 10L));
    }

    @Test
    void commitCheckout_expiredQuote_releasesHoldAndThrows() {
        CheckoutQuote quote = new CheckoutQuote();
        quote.setId(10L);
        quote.setCustomerId(1L);
        quote.setStatus(CheckoutQuote.QuoteStatus.OPEN);
        quote.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        quote.setAppliedSubscriptionId(null);
        quote.setAppliedDiscountId(null);

        when(quoteRepository.findById(10L)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(any(CheckoutQuote.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(QuoteExpiredException.class, () -> checkoutService.commitCheckout(200L, 10L));
        assertEquals(CheckoutQuote.QuoteStatus.EXPIRED, quote.getStatus());
    }

    @Test
    void commitCheckout_unknownQuote_throwsNotFound() {
        when(quoteRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> checkoutService.commitCheckout(1L, 999L));
    }

    // --- Rollback: gives back exactly what commit consumed ---

    @Test
    void rollbackCheckout_restoresVisitAndDiscountUse() {
        CheckoutQuote quote = new CheckoutQuote();
        quote.setId(10L);
        quote.setStatus(CheckoutQuote.QuoteStatus.CONSUMED);
        quote.setConsumedByOrderReferenceId(500L);
        quote.setAppliedSubscriptionId(60L);
        quote.setAppliedDiscountId(77L);
        quote.setRolledBack(false);

        Discount discount = activeDiscount(77L, "SAVE10", BigDecimal.TEN);
        discount.setUsesCount(5);

        DiscountRedemption redemption = new DiscountRedemption();
        redemption.setId(900L);
        redemption.setStatus(DiscountRedemption.RedemptionStatus.CONFIRMED);

        when(quoteRepository.findByConsumedByOrderReferenceIdAndStatusAndRolledBackFalse(500L, CheckoutQuote.QuoteStatus.CONSUMED))
                .thenReturn(Optional.of(quote));
        when(discountRepository.findById(77L)).thenReturn(Optional.of(discount));
        when(redemptionRepository.findByQuoteId(10L)).thenReturn(Optional.of(redemption));
        when(quoteRepository.save(any(CheckoutQuote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(discountRepository.save(any(Discount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(redemptionRepository.save(any(DiscountRedemption.class))).thenAnswer(inv -> inv.getArgument(0));

        checkoutService.rollbackCheckout(500L);

        verify(subscriptionRepository).restoreVisit(60L);
        assertEquals(4, discount.getUsesCount());
        assertEquals(DiscountRedemption.RedemptionStatus.REVOKED, redemption.getStatus());
        assertTrue(quote.isRolledBack());
    }

    @Test
    void rollbackCheckout_nothingCommittedForThatOrder_isAnIdempotentNoOp() {
        when(quoteRepository.findByConsumedByOrderReferenceIdAndStatusAndRolledBackFalse(999L, CheckoutQuote.QuoteStatus.CONSUMED))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> checkoutService.rollbackCheckout(999L));
        verifyNoInteractions(subscriptionRepository, discountRepository, redemptionRepository);
    }

    // --- Sweep: releases every expired open hold system-wide ---

    @Test
    void sweepExpiredQuotes_releasesEachExpiredOpenQuote() {
        CheckoutQuote quote = new CheckoutQuote();
        quote.setId(10L);
        quote.setStatus(CheckoutQuote.QuoteStatus.OPEN);
        quote.setAppliedSubscriptionId(60L);
        quote.setAppliedDiscountId(null);

        when(quoteRepository.findByStatusAndExpiresAtBefore(eq(CheckoutQuote.QuoteStatus.OPEN), any()))
                .thenReturn(List.of(quote));
        when(quoteRepository.save(any(CheckoutQuote.class))).thenAnswer(inv -> inv.getArgument(0));

        checkoutService.sweepExpiredQuotes();

        verify(subscriptionRepository).restoreVisit(60L);
        assertEquals(CheckoutQuote.QuoteStatus.EXPIRED, quote.getStatus());
    }
}
