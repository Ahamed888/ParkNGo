package com.example.parkngo.discount.exception;

/**
 * Thrown when an admin tries to hard-delete a Package or Discount that is
 * still referenced elsewhere (an active Subscription for a Package, a
 * Reservation for a Discount) and the database's own foreign-key constraint
 * rejected the delete. The service translates that low-level
 * DataIntegrityViolationException into this domain-specific exception so the
 * controller/template only ever has to reason about ParkNGo concepts.
 */
public class ResourceInUseException extends RuntimeException {
    public ResourceInUseException(String message) {
        super(message);
    }
}
