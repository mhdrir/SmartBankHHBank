package com.smartbank.payments.domain;

/**
 * A business error (not a technical one): retrying will not help.
 * The BookPaymentDelegate turns it into a BpmnError so the process takes the "rejected" path
 * instead of retrying the job.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String iban) {
        super("Insufficient funds on account " + iban);
    }
}
