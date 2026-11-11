package com.example.parkngo.checkout;

import com.example.parkngo.checkout.exception.QuoteExpiredException;
import com.example.parkngo.checkout.exception.QuoteMismatchException;
import com.example.parkngo.discount.Discount;
import com.example.parkngo.discount.Package;
import com.example.parkngo.discount.DiscountRepository;
import com.example.parkngo.discount.exception.ResourceNotFoundException;
import com.example.parkngo.subscription.Subscription;
import com.example.parkngo.subscription.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CheckoutService.java
 * When both an active membership pass and a discount code are available,
 * this tentatively holds both, compares the resulting price, and keeps
 * whichever one actually saves the customer more - releasing the other
 * hold straight back. Only when just one of the two is available does that
 * one applies outright. All of this rides on the same
 * hold-at-preview / finalize-at-commit / release-on-expiry pattern a real
 * ticket or airline checkout uses, so a customer can never be quoted a
 * price against a resource (a package visit, a capped discount code) that
 * isn't actually, provisionally reserved for them the instant they see it.
 *
 * Preview (previewCheckout) places the hold and returns a quote. Commit
 * (commitCheckout) only ever finalizes a hold that preview already placed -
 * it never re-checks capacity or re-derives the price, which is what makes
 * commit both race-safe and safely retriable. An unconsumed quote's hold is
 * given back automatically, either the next time its own customer starts a
 * new preview, or - for a customer who simply never returns - by the
 * scheduled sweep below.
 *
 * Reservation and subscription purchases both use this quote flow.
 */
@Service
@Transactional
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
    private static final int QUOTE_TTL_MINUTES = 5;

    private final CheckoutQuoteRepository quoteRepository;
    private final DiscountRedemptionRepository redemptionRepository;
    private final DiscountRepository discountRepository;
    private final SubscriptionRepository subscriptionRepository;

    public CheckoutService(CheckoutQuoteRepository quoteRepository,
                            DiscountRedemptionRepository redemptionRepository,
                            DiscountRepository discountRepository,
                            SubscriptionRepository subscriptionRepository) {
        this.quoteRepository = quoteRepository;
        this.redemptionRepository = redemptionRepository;
        this.discountRepository = discountRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Full preview: considers an active package pass and a discount code.
     */
    public CheckoutQuote previewCheckout(Long customerId, BigDecimal amount, String discountCode, LocalDateTime when) {
        return preview(customerId, amount, discountCode, when, true, null);
    }

    /** Preview a reservation checkout for one parking complex. A package that is
     * not assigned to this complex is deliberately ignored. */
    public CheckoutQuote previewCheckoutForFacility(Long customerId, BigDecimal amount, String discountCode,
                                                     LocalDateTime when, Long facilityId) {
        return preview(customerId, amount, discountCode, when, true, facilityId);
    }

    /**
     * Preview for buying a NEW package: deliberately skips the package-hold
     * step (spending an existing pass to buy a new one is not a meaningful
     * concept), but still lets a discount code reduce the purchase price.
     */
    public CheckoutQuote previewPackagePurchase(Long customerId, BigDecimal amount, String discountCode, LocalDateTime when) {
        return preview(customerId, amount, discountCode, when, false, null);
    }

    private CheckoutQuote preview(Long customerId, BigDecimal amount, String discountCode, LocalDateTime when,
                                  boolean allowPackageHold, Long facilityId) {
        if (when == null) {
            when = LocalDateTime.now();
        }

        // Persist the quote shell FIRST, before anything references its id -
        // a discount hold needs a real, already-saved quote id to point its
        // DiscountRedemption row at.
        CheckoutQuote quote = new CheckoutQuote();
        quote.setCustomerId(customerId);
        quote.setOriginalAmount(amount);
        quote.setFinalAmount(amount);
        quote.setExpiresAt(when.plusMinutes(QUOTE_TTL_MINUTES));
        quote.setStatus(CheckoutQuote.QuoteStatus.OPEN);
        quote = quoteRepository.save(quote);

        // Tentatively hold BOTH a package visit and a discount code, if
        // either is available/valid, so they can be compared on price before
        // deciding which one to actually keep. This does mean a capped
        // discount code's use gets counted for a moment even if the package
        // ends up winning - releaseDiscountHold immediately below gives that
        // slot straight back, same as the existing expired-quote sweep does.
        Subscription heldSubscription = null;
        BigDecimal packageFinalAmount = null;
        if (allowPackageHold) {
            heldSubscription = tryHoldPackageVisit(customerId, facilityId);
            if (heldSubscription != null) {
                // The pass reduces the price by the package's configured
                // discount percentage - it does not zero it out. A
                // visit-limited pass still spends one visit per booking
                // (tryHoldPackageVisit above), it just no longer means "this
                // booking is free"; an unlimited pass applies this same
                // discount to every booking for as long as it's active.
                // Configurable membership benefits supersede the legacy package
                // percentage. Older memberships keep their original behaviour.
                BigDecimal discountPct = heldSubscription.getMembershipPackage().getParkingDiscountPercentage();
                packageFinalAmount = amount
                        .multiply(BigDecimal.ONE.subtract(discountPct.divide(BigDecimal.valueOf(100))))
                        .max(BigDecimal.ZERO);
            }
        }

        HeldDiscount heldDiscount = null;
        if (discountCode != null && !discountCode.isBlank()) {
            heldDiscount = tryHoldDiscount(discountCode, customerId, quote.getId(), amount, when);
            // Invalid, expired, off-peak-mismatched, or capped code just
            // means heldDiscount stays null - fall through rather than
            // failing the whole checkout outright.
        }

        if (heldSubscription != null && heldDiscount != null) {
            // Both are available for this booking - keep whichever actually
            // costs the customer less, and give the other one straight back.
            if (packageFinalAmount.compareTo(heldDiscount.finalAmount()) <= 0) {
                releaseDiscountHold(quote.getId(), heldDiscount.discountId());
                quote.setAppliedSubscriptionId(heldSubscription.getId());
                quote.setFinalAmount(packageFinalAmount);
                log.info("Checkout quote {} used package visit from subscription {} over discount {} (cheaper for the customer)",
                        quote.getId(), heldSubscription.getId(), heldDiscount.discountId());
            } else {
                subscriptionRepository.restoreVisit(heldSubscription.getId());
                quote.setAppliedDiscountId(heldDiscount.discountId());
                quote.setFinalAmount(heldDiscount.finalAmount());
                log.info("Checkout quote {} used discount {} over package visit from subscription {} (cheaper for the customer)",
                        quote.getId(), heldDiscount.discountId(), heldSubscription.getId());
            }
            return quoteRepository.save(quote);
        }

        if (heldSubscription != null) {
            quote.setAppliedSubscriptionId(heldSubscription.getId());
            quote.setFinalAmount(packageFinalAmount);
            log.info("Checkout quote {} held package visit from subscription {}", quote.getId(), heldSubscription.getId());
            return quoteRepository.save(quote);
        }

        if (heldDiscount != null) {
            quote.setAppliedDiscountId(heldDiscount.discountId());
            quote.setFinalAmount(heldDiscount.finalAmount());
            log.info("Checkout quote {} held discount {}", quote.getId(), heldDiscount.discountId());
            return quoteRepository.save(quote);
        }

        return quoteRepository.save(quote);
    }

    /**
     * Finalizes an already-held quote against a specific order. Idempotent:
     * calling this again with the same (orderReferenceId, quoteId) pair -
     * a retried request, a duplicate webhook - returns the same result
     * without touching any counter a second time. Calling it with the SAME
     * quoteId but a DIFFERENT orderReferenceId is rejected outright, since a
     * quote may only ever be spent once.
     */
    public CheckoutQuote commitCheckout(Long orderReferenceId, Long quoteId) {
        CheckoutQuote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout quote not found: " + quoteId));

        if (quote.getStatus() == CheckoutQuote.QuoteStatus.CONSUMED) {
            if (orderReferenceId != null && orderReferenceId.equals(quote.getConsumedByOrderReferenceId())) {
                return quote;
            }
            throw new QuoteMismatchException("This checkout quote was already applied to a different order");
        }

        if (quote.getStatus() == CheckoutQuote.QuoteStatus.EXPIRED || quote.getExpiresAt().isBefore(LocalDateTime.now())) {
            if (quote.getStatus() == CheckoutQuote.QuoteStatus.OPEN) {
                releaseHold(quote);
            }
            throw new QuoteExpiredException("Checkout quote expired - request a new one via preview");
        }

        if (quote.getAppliedDiscountId() != null) {
            redemptionRepository.findByQuoteId(quote.getId()).ifPresent(r -> {
                r.setStatus(DiscountRedemption.RedemptionStatus.CONFIRMED);
                r.setOrderReferenceId(orderReferenceId);
                redemptionRepository.save(r);
            });
        }

        quote.setStatus(CheckoutQuote.QuoteStatus.CONSUMED);
        quote.setConsumedByOrderReferenceId(orderReferenceId);
        CheckoutQuote saved = quoteRepository.save(quote);
        log.info("Checkout quote {} committed to order reference {}", quote.getId(), orderReferenceId);
        return saved;
    }

    /**
     * Reverses a previously committed order (e.g. a refund), giving back
     * whatever visit/discount slot it consumed. Not for a failed payment -
     * commit only ever runs after payment succeeds, so a failed payment
     * never reaches commit in the first place and there is nothing to roll
     * back for it. Idempotent via the rolledBack flag.
     *
     * A no-op (not an error) when there's nothing to roll back - a walk-in
     * reservation never went through the checkout hold at all, and even an
     * online booking may never have reached a CONSUMED quote (e.g. the
     * commit step failed at booking time, or it was already rolled back by
     * an earlier cancel attempt). This used to throw here, which - since
     * this method used to just join the caller's own transaction - marked
     * that whole transaction rollback-only even though the caller wrapped
     * the call in a try/catch expecting it to be harmless; catching the
     * exception doesn't undo that, so cancelling a reservation with nothing
     * to roll back failed outright with "UnexpectedRollbackException:
     * ...marked as rollback-only" instead of just cancelling normally.
     *
     * Joins the cancellation transaction. A separate transaction would query
     * while SQL Server still holds the cancellation's reservation locks and
     * can block indefinitely. The operation is safe to join because an absent
     * checkout quote is deliberately a no-op rather than an exception.
     */
    public void rollbackCheckout(Long orderReferenceId) {
        Optional<CheckoutQuote> maybeQuote = quoteRepository
                .findByConsumedByOrderReferenceIdAndStatusAndRolledBackFalse(orderReferenceId, CheckoutQuote.QuoteStatus.CONSUMED);
        if (maybeQuote.isEmpty()) {
            log.info("Nothing to roll back for order reference {} (no consumed, not-yet-rolled-back checkout quote)", orderReferenceId);
            return;
        }
        CheckoutQuote quote = maybeQuote.get();

        if (quote.getAppliedSubscriptionId() != null) {
            subscriptionRepository.restoreVisit(quote.getAppliedSubscriptionId());
        }
        if (quote.getAppliedDiscountId() != null) {
            discountRepository.findById(quote.getAppliedDiscountId()).ifPresent(d -> {
                d.setUsesCount(Math.max(0, d.getUsesCount() - 1));
                discountRepository.save(d);
            });
            redemptionRepository.findByQuoteId(quote.getId()).ifPresent(r -> {
                r.setStatus(DiscountRedemption.RedemptionStatus.REVOKED);
                redemptionRepository.save(r);
            });
        }
        quote.setRolledBack(true);
        quoteRepository.save(quote);
        log.info("Checkout quote {} rolled back for order reference {}", quote.getId(), orderReferenceId);
    }

    /**
     * Releases every expired, still-OPEN hold in the system, regardless of
     * which customer created it - this is what protects against a customer
     * who previews, holds a visit or a discount slot, and simply never
     * comes back. Piggybacking this on a scheduled tick (rather than only
     * ever releasing lazily per-customer) guarantees an abandoned hold is
     * freed within one sweep interval even if its own customer never starts
     * another preview.
     */
    @Scheduled(fixedDelay = 120_000)
    public void sweepExpiredQuotes() {
        List<CheckoutQuote> expired = quoteRepository.findByStatusAndExpiresAtBefore(CheckoutQuote.QuoteStatus.OPEN, LocalDateTime.now());
        if (!expired.isEmpty()) {
            log.info("Sweeping {} expired checkout quote(s)", expired.size());
        }
        expired.forEach(this::releaseHold);
    }

    private void releaseExpiredHoldsFor(Long customerId) {
        List<CheckoutQuote> mine = quoteRepository.findByCustomerIdAndStatusAndExpiresAtBefore(
                customerId, CheckoutQuote.QuoteStatus.OPEN, LocalDateTime.now());
        mine.forEach(this::releaseHold);
    }

    private void releaseHold(CheckoutQuote quote) {
        if (quote.getStatus() != CheckoutQuote.QuoteStatus.OPEN) {
            return;
        }
        if (quote.getAppliedSubscriptionId() != null) {
            subscriptionRepository.restoreVisit(quote.getAppliedSubscriptionId());
        }
        if (quote.getAppliedDiscountId() != null) {
            redemptionRepository.deleteByQuoteIdAndStatus(quote.getId(), DiscountRedemption.RedemptionStatus.HELD);
            discountRepository.findById(quote.getAppliedDiscountId()).ifPresent(d -> {
                d.setUsesCount(Math.max(0, d.getUsesCount() - 1));
                discountRepository.save(d);
            });
        }
        quote.setStatus(CheckoutQuote.QuoteStatus.EXPIRED);
        quoteRepository.save(quote);
    }

    /**
     * Gives back a discount-code hold that was placed during this same
     * preview but lost the price comparison to a membership pass - same
     * bookkeeping releaseHold(quote) does for an expired quote's discount
     * hold, just invoked immediately instead of by the sweep.
     */
    private void releaseDiscountHold(Long quoteId, Long discountId) {
        redemptionRepository.deleteByQuoteIdAndStatus(quoteId, DiscountRedemption.RedemptionStatus.HELD);
        discountRepository.findById(discountId).ifPresent(d -> {
            d.setUsesCount(Math.max(0, d.getUsesCount() - 1));
            discountRepository.save(d);
        });
    }

    /** Attempts to hold one visit of the customer's next-to-expire active pass. Null if none is available/holdable. */
    private Subscription tryHoldPackageVisit(Long customerId, Long facilityId) {
        releaseExpiredHoldsFor(customerId);

        if (facilityId == null) {
            Optional<Subscription> candidate = subscriptionRepository
                    .findFirstByCustomer_IdAndStatusOrderByEndDateAsc(customerId, Subscription.SubscriptionStatus.ACTIVE);
            if (candidate.isEmpty()) {
                return null;
            }
            return holdCandidate(candidate.get());
        }

        List<Subscription> candidates = subscriptionRepository.findByCustomer_IdOrderByIdDesc(customerId).stream()
                .filter(subscription -> subscription.getStatus() == Subscription.SubscriptionStatus.ACTIVE)
                .filter(subscription -> subscription.getMembershipPackage() != null)
                .filter(subscription -> subscription.getMembershipPackage().getParkingDiscountPercentage().signum() > 0)
                .filter(subscription -> facilityId == null || subscription.getMembershipPackage().isEligibleForFacility(facilityId))
                .sorted(java.util.Comparator.comparing(Subscription::getEndDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
        for (Subscription candidate : candidates) {
            Subscription held = holdCandidate(candidate);
            if (held != null) return held;
        }
        return null;
    }

    private Subscription holdCandidate(Subscription candidate) {
        if (candidate.getStartDate() != null && candidate.getStartDate().isAfter(LocalDate.now())) return null;
        if (candidate.getEndDate() != null && candidate.getEndDate().isBefore(LocalDate.now())) {
            candidate.setStatus(Subscription.SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(candidate);
            return null;
        }
        return subscriptionRepository.tryDecrementVisit(candidate.getId()) > 0 ? candidate : null;
    }

    /** Attempts to hold one redemption slot of a discount code. Null if invalid/expired/capped/off-peak-mismatched. */
    private HeldDiscount tryHoldDiscount(String code, Long customerId, Long quoteId, BigDecimal amount, LocalDateTime when) {
        String normalized = code.trim().toUpperCase();
        Optional<Discount> maybeDiscount = discountRepository.findByCodeForUpdate(normalized);
        if (maybeDiscount.isEmpty()) {
            return null;
        }
        Discount discount = maybeDiscount.get();

        if (discount.getStatus() != Discount.DiscountStatus.ACTIVE) {
            return null;
        }
        LocalDate today = when.toLocalDate();
        if (discount.getValidFromDate() != null && today.isBefore(discount.getValidFromDate())) {
            return null;
        }
        if (discount.getValidToDate() != null && today.isAfter(discount.getValidToDate())) {
            return null;
        }
        if (discount.getAppliesTo() == Discount.AppliesTo.OFF_PEAK_ONLY && !isOffPeak(when, discount)) {
            return null;
        }
        if (discount.getMaxUses() != null && discount.getUsesCount() >= discount.getMaxUses()) {
            return null;
        }
        long perCustomerCount = redemptionRepository.countByDiscountIdAndCustomerIdAndStatusIn(
                discount.getId(), customerId,
                List.of(DiscountRedemption.RedemptionStatus.HELD, DiscountRedemption.RedemptionStatus.CONFIRMED));
        if (discount.getMaxUsesPerCustomer() != null && perCustomerCount >= discount.getMaxUsesPerCustomer()) {
            return null;
        }

        DiscountRedemption redemption = new DiscountRedemption();
        redemption.setDiscountId(discount.getId());
        redemption.setCustomerId(customerId);
        redemption.setQuoteId(quoteId);
        redemption.setStatus(DiscountRedemption.RedemptionStatus.HELD);
        redemptionRepository.save(redemption);

        discount.setUsesCount(discount.getUsesCount() + 1);
        discountRepository.save(discount);

        BigDecimal finalAmount;
        if (discount.getDiscountType() == Discount.DiscountType.PERCENTAGE) {
            finalAmount = amount.multiply(BigDecimal.ONE.subtract(discount.getValue().divide(BigDecimal.valueOf(100))));
        } else {
            finalAmount = amount.subtract(discount.getValue()).max(BigDecimal.ZERO);
        }
        return new HeldDiscount(discount.getId(), finalAmount);
    }

    /** Self-contained off-peak window check (facility hours are not owned by this module - see the integration note). */
    private boolean isOffPeak(LocalDateTime when, Discount discount) {
        Integer start = discount.getOffPeakStartHour();
        Integer end = discount.getOffPeakEndHour();
        if (start == null || end == null) {
            return false; // misconfigured - fail closed, the code simply won't apply
        }
        int hour = when.getHour();
        if (start.intValue() < end.intValue()) {
            return hour >= start && hour < end;
        }
        // Window wraps past midnight, e.g. 22 -> 6.
        return hour >= start || hour < end;
    }

    private record HeldDiscount(Long discountId, BigDecimal finalAmount) {
    }
}
