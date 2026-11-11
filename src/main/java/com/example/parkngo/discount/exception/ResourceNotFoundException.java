package com.example.parkngo.discount.exception;

/**
 * Thrown when a Package or Discount coupon is looked up by id/code and does
 * not exist. Unchecked (extends RuntimeException) so service and controller
 * method signatures stay clean - callers that care catch this specific type
 * instead of a blanket Exception.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
