package com.smartbank.payments.application;

/**
 * The client re-used an Idempotency-Key for a DIFFERENT request body.
 * Mapped to HTTP 422 Unprocessable Content (same behaviour as e.g. Stripe's API).
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency-Key '" + key + "' was already used for a different request");
    }
}
