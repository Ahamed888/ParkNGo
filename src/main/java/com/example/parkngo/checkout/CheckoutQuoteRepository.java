package com.example.parkngo.checkout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CheckoutQuoteRepository extends JpaRepository<CheckoutQuote, Long> {

    /** Every quote in the system whose hold has run past its expiry - used by the global sweep. */
    List<CheckoutQuote> findByStatusAndExpiresAtBefore(CheckoutQuote.QuoteStatus status, LocalDateTime time);

    /** One customer's own abandoned quotes - released lazily whenever they start a new preview. */
    List<CheckoutQuote> findByCustomerIdAndStatusAndExpiresAtBefore(Long customerId, CheckoutQuote.QuoteStatus status, LocalDateTime time);

    /** The committed quote behind a given order reference, for rollback - excludes one already rolled back. */
    Optional<CheckoutQuote> findByConsumedByOrderReferenceIdAndStatusAndRolledBackFalse(Long orderReferenceId, CheckoutQuote.QuoteStatus status);
}
