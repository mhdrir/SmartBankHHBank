package com.smartbank.payments.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox every second and publishes unpublished events to Kafka.
 *
 * Guarantees and trade-offs (good interview talking points):
 *  - At-least-once: if the app crashes after Kafka acknowledged the send but before the
 *    "published_at" update commits, the event is sent again on restart. Consumers de-duplicate.
 *  - Ordering: events are read in created_at order and sent with a key, so per payment/account
 *    the order is kept within a partition.
 *  - Scaling: with several app instances you would lock rows with
 *    "SELECT ... FOR UPDATE SKIP LOCKED" (or use Debezium CDC instead of polling)
 *    so two instances never send the same batch. Kept simple here on purpose.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${smartbank.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                // .get(...) = wait for the broker acknowledgement (acks=all in application.yml).
                // Only after Kafka confirmed do we mark the row as published.
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                event.markPublished();
                log.debug("Published {} {} to {}", event.getEventType(), event.getId(), event.getTopic());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // stop; remaining events are retried on the next run
            } catch (Exception e) {
                // Kafka not reachable: stop this batch, keep order, try again next second.
                log.warn("Could not publish outbox event {}: {}", event.getId(), e.getMessage());
                return;
            }
        }
    }
}
