package com.smartbank.payments.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Simulates a separate "notification service".
 *
 * Different consumer group ("notification-service") -> it receives ALL result events
 * independently of any other consumer. That is the core idea of pub/sub with Kafka:
 * the producer does not know who listens; new consumers can be added without changing it.
 * In a real system this would live in its own deployable and send e-mail/push (e.g. AWS SES/SNS).
 */
@Component
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final ObjectMapper objectMapper;

    public PaymentResultListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {Topics.PAYMENTS_COMPLETED, Topics.PAYMENTS_REJECTED}, groupId = "notification-service")
    public void onResult(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic)
            throws JsonProcessingException {
        PaymentEvent event = switch (topic) {
            case Topics.PAYMENTS_COMPLETED -> objectMapper.readValue(message, PaymentEvent.PaymentCompleted.class);
            case Topics.PAYMENTS_REJECTED -> objectMapper.readValue(message, PaymentEvent.PaymentRejected.class);
            default -> throw new IllegalArgumentException("Unexpected topic " + topic);
        };

        // Pattern matching for switch over a sealed interface (Java 21): the compiler checks that
        // every permitted event type is handled - no default branch needed.
        String text = switch (event) {
            case PaymentEvent.PaymentCompleted c ->
                    "Your transfer of %s %s to %s was executed.".formatted(c.amount(), c.currency(), c.creditorIban());
            case PaymentEvent.PaymentRejected r -> "Your transfer was rejected: " + r.reason();
            case PaymentEvent.PaymentRequested q -> "Transfer received.";
        };
        log.info("[NOTIFICATION] payment {}: {}", event.paymentId(), text);
    }
}
