package com.example.parkngo.discount.exception;

/**
 * Thrown when a Package name or Discount coupon code being saved collides
 * with an existing, different record. Kept separate from
 * ResourceNotFoundException so a controller can react differently (e.g.
 * re-render the form with a field-level error) instead of treating every
 * business-rule failure the same way.
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
