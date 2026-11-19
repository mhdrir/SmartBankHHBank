package com.smartbank.payments.application;

import java.util.UUID;

/** Mapped to HTTP 404 by the GlobalExceptionHandler. */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID id) {
        super("Payment " + id + " not found");
    }
}
