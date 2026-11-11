package com.example.parkngo.checkout.exception;

/**
 * Thrown when POST /checkout/commit is called with a quote whose 5-minute
 * hold window has already elapsed. The caller should request a fresh quote
 * via preview rather than retry the same one.
 */
public class QuoteExpiredException extends RuntimeException {
    public QuoteExpiredException(String message) {
        super(message);
    }
}
