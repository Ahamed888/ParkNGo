package com.example.parkngo.checkout.exception;

/**
 * Thrown when a checkout quote has already been consumed by a DIFFERENT
 * order than the one now trying to commit it. Distinct from a same-order
 * retry (which is a safe, idempotent no-op) - this is a genuine conflict
 * that should never happen from a well-behaved caller, only from a bug or a
 * client resubmitting a stale quote id against the wrong order.
 */
public class QuoteMismatchException extends RuntimeException {
    public QuoteMismatchException(String message) {
        super(message);
    }
}
