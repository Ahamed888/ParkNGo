package com.example.parkngo.reservation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom domain exception for Reservation validation errors.
 * Carries both a primary message and a field-level error mapping
 * to enable precise, user-friendly UI highlighting.
 */
public class ReservationValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ReservationValidationException(String message) {
        super(message);
        this.fieldErrors = new HashMap<>();
    }

    public ReservationValidationException(String message, String fieldName, String fieldError) {
        super(message);
        this.fieldErrors = new HashMap<>();
        this.fieldErrors.put(fieldName, fieldError);
    }

    public ReservationValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors != null ? new HashMap<>(fieldErrors) : new HashMap<>();
    }

    public Map<String, String> getFieldErrors() {
        return Collections.unmodifiableMap(fieldErrors);
    }

    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }
}
