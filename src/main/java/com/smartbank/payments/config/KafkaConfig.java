package com.smartbank.payments.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.smartbank.payments.messaging.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka topics and error handling.
 *
 * Serialisation: keys and values are plain Strings (configured in application.yml); events are
 * JSON strings written by the outbox. Simple, language-neutral, easy to inspect in Kafka UI.
 * (Larger organisations often use Avro/Protobuf + a Schema Registry for versioned contracts.)
 */
@Configuration
public class KafkaConfig {

    /**
     * NewTopic beans are created by Spring's KafkaAdmin on startup if they do not exist.
     * 3 partitions = up to 3 consumers of one group can work in parallel.
     * Replicas = 1 only because the local broker is a single node; production uses 3 (+ min.insync.replicas=2).
     */
    @Bean
    NewTopic paymentsRequestedTopic() {
        return TopicBuilder.name(Topics.PAYMENTS_REQUESTED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentsRequestedDltTopic() {
        // DLT needs at least as many partitions: the recoverer writes to the same partition number.
        return TopicBuilder.name(Topics.PAYMENTS_REQUESTED_DLT).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentsCompletedTopic() {
        return TopicBuilder.name(Topics.PAYMENTS_COMPLETED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentsRejectedTopic() {
        return TopicBuilder.name(Topics.PAYMENTS_REJECTED).partitions(3).replicas(1).build();
    }

    /**
     * Error handling for ALL @KafkaListener methods (Spring Boot plugs this bean into the
     * default listener container factory automatically).
     *
     * - Retry a failing record 3 times with 1 s pause (FixedBackOff(interval, maxAttempts)).
     * - Then publish it to "<topic>.DLT" and continue with the next record.
     * - JSON errors are "not retryable": a broken message stays broken, retrying wastes time.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(1_000L, 3));
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }
}
