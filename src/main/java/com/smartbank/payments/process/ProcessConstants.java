package com.smartbank.payments.process;

/**
 * Names shared between Java code and the BPMN file (src/main/resources/bpmn/payment-process.bpmn).
 * A typo here = a runtime error, so keep them in one place.
 */
public final class ProcessConstants {

    public static final String PROCESS_KEY = "payment-process";

    // process variables
    public static final String VAR_PAYMENT_ID = "paymentId";
    public static final String VAR_AMOUNT = "amount";                         // shown in Tasklist
    public static final String VAR_DEBTOR_IBAN = "debtorIban";
    public static final String VAR_REQUIRES_MANUAL_REVIEW = "requiresManualReview";
    public static final String VAR_APPROVED = "approved";                     // set by the reviewer
    public static final String VAR_REJECTION_CODE = "rejectionCode";          // set by error boundary events
    public static final String VAR_REJECTION_REASON = "rejectionReason";

    // BPMN error codes (BpmnError = expected business outcome, not a technical failure)
    public static final String ERROR_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String ERROR_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    private ProcessConstants() {
    }
}
