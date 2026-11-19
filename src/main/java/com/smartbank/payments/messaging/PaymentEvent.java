package com.smartbank.payments.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * All payment events share an eventId (for de-duplication), the paymentId and a timestamp.
 *
 * "sealed" (Java 17): only the records listed in "permits" can implement this interface.
 * The compiler then knows every possible event type, e.g. for exhaustive switch expressions.
 * Records (Java 16): immutable data carriers; equals/hashCode/toString generated; Jackson can
 * (de)serialise them out of the box.
 */
public sealed interface PaymentEvent
        permits PaymentEvent.PaymentRequested, PaymentEvent.PaymentCompleted, PaymentEvent.PaymentRejected {

    UUID eventId();

    UUID paymentId();

    Instant occurredAt();

    /** Published when the API accepted a payment. Consumed by the orchestrator. */
    record PaymentRequested(UUID eventId, UUID paymentId, String debtorIban, String creditorIban,
                            BigDecimal amount, String currency, Instant occurredAt) implements PaymentEvent {
    }

    /** Published at the end of the happy path. Consumed e.g. by notifications, accounting, reporting. */
    record PaymentCompleted(UUID eventId, UUID paymentId, String debtorIban, String creditorIban,
                            BigDecimal amount, String currency, Instant occurredAt) implements PaymentEvent {
    }

    /** Published when validation, booking or the manual review rejected the payment. */
    record PaymentRejected(UUID eventId, UUID paymentId, String reason, Instant occurredAt)
            implements PaymentEvent {
    }
}
