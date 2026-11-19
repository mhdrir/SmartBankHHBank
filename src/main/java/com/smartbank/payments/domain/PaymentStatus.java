package com.smartbank.payments.domain;

/**
 * Lifecycle of a payment. Stored as text (@Enumerated(STRING)) so the DB stays readable
 * and adding a value in the middle never shifts existing rows (which ORDINAL would do).
 *
 * RECEIVED        -> API accepted it, event not yet processed
 * PROCESSING      -> Camunda process started
 * PENDING_REVIEW  -> waiting for a human (four-eyes principle) because of a high amount
 * BOOKED          -> debtor debited, creditor credited
 * COMPLETED       -> final, success event published
 * REJECTED        -> final, rejection event published (validation error, insufficient funds, review declined)
 */
public enum PaymentStatus {
    RECEIVED,
    PROCESSING,
    PENDING_REVIEW,
    BOOKED,
    COMPLETED,
    REJECTED;

    public boolean isFinal() {
        return this == COMPLETED || this == REJECTED;
    }
}
