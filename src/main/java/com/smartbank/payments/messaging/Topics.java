package com.smartbank.payments.messaging;

/**
 * Kafka topic names in one place. Naming convention: <domain>.<event in past tense>.
 * Events describe facts that already happened ("requested", "completed"), not commands.
 */
public final class Topics {

    public static final String PAYMENTS_REQUESTED = "payments.requested";
    public static final String PAYMENTS_COMPLETED = "payments.completed";
    public static final String PAYMENTS_REJECTED = "payments.rejected";

    /** Dead-letter topic: spring-kafka's default is "<original topic>.DLT". */
    public static final String PAYMENTS_REQUESTED_DLT = PAYMENTS_REQUESTED + ".DLT";

    private Topics() {
    }
}
