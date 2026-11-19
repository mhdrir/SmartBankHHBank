package com.smartbank.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point.
 *
 * Flow in one sentence:
 * REST API stores a payment + an outbox event in ONE transaction
 *   -> OutboxRelay publishes the event to Kafka ("payments.requested")
 *   -> PaymentRequestedListener consumes it and starts the Camunda process
 *   -> the BPMN process validates, optionally waits for a four-eyes review, books, and completes/rejects
 *   -> result events go to Kafka ("payments.completed" / "payments.rejected")
 *   -> PaymentResultListener (a separate consumer group) simulates the notification service.
 *
 * @EnableScheduling     -> needed for the @Scheduled OutboxRelay
 * @ConfigurationPropertiesScan -> picks up the type-safe SmartBankProperties record
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class SmartBankPaymentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartBankPaymentsApplication.class, args);
    }
}
