package com.smartbank.payments.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox pattern.
 *
 * Problem ("dual write"): we must (a) save the payment in the DB and (b) publish an event to Kafka.
 * These are two different systems; there is no shared transaction. If the app crashes between (a)
 * and (b) we have a payment nobody processes; if (b) happens and (a) rolls back we have an event
 * for a payment that does not exist.
 *
 * Solution: in the SAME DB transaction as the business change we insert a row into this table.
 * A separate relay (OutboxRelay) reads unpublished rows and sends them to Kafka afterwards.
 * Result: "saved" and "will be sent" are atomic. Delivery becomes at-least-once, so consumers
 * must be idempotent (see ProcessedEvent).
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    /** Also used as the eventId inside the payload, so consumers can de-duplicate. */
    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 100)
    private String topic;

    /** Kafka message key -> decides the partition -> guarantees ordering per key. */
    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** JSON. Stored as text so the relay does not need to know the event classes. */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateId, String topic, String messageKey,
                       String eventType, String payload) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
