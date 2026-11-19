package com.smartbank.payments.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbank.payments.messaging.PaymentEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Small helper that serialises an event and stores it in the outbox.
 *
 * Propagation.MANDATORY: this method MUST be called inside an existing transaction.
 * If someone calls it without one, Spring throws immediately - which protects the core
 * guarantee of the outbox pattern (business change + event in the same transaction).
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String topic, String messageKey, PaymentEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            repository.save(new OutboxEvent(
                    event.eventId(),
                    event.paymentId().toString(),
                    topic,
                    messageKey,
                    event.getClass().getSimpleName(),
                    json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + event, e);
        }
    }
}
