package com.smartbank.payments.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbank.payments.application.PaymentService;
import com.smartbank.payments.process.ProcessConstants;
import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Bridge Kafka -> Camunda: every "PaymentRequested" event starts one process instance.
 *
 * Consumer group "payment-orchestrator": the 3 partitions of payments.requested are spread over
 * all running instances of this app (horizontal scaling). Order is kept per key (= debtor IBAN).
 *
 * One DB transaction covers: de-duplication check, status update, process start, inbox insert.
 * The Kafka offset is committed AFTER the method returns (ack-mode RECORD), so a crash before
 * that means redelivery - which the ProcessedEvent check makes harmless.
 *
 * If the method throws, the DefaultErrorHandler (KafkaConfig) retries 3 times, then sends the
 * record to "payments.requested.DLT" so one poison message cannot block the partition.
 */
@Component
public class PaymentRequestedListener {

    static final String CONSUMER_NAME = "payment-orchestrator";
    private static final Logger log = LoggerFactory.getLogger(PaymentRequestedListener.class);

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEvents;
    private final PaymentService paymentService;
    private final RuntimeService runtimeService;

    public PaymentRequestedListener(ObjectMapper objectMapper, ProcessedEventRepository processedEvents,
                                    PaymentService paymentService, RuntimeService runtimeService) {
        this.objectMapper = objectMapper;
        this.processedEvents = processedEvents;
        this.paymentService = paymentService;
        this.runtimeService = runtimeService;
    }

    @KafkaListener(topics = Topics.PAYMENTS_REQUESTED, groupId = CONSUMER_NAME)
    @Transactional
    public void onPaymentRequested(String message) throws JsonProcessingException {
        PaymentEvent.PaymentRequested event = objectMapper.readValue(message, PaymentEvent.PaymentRequested.class);

        if (processedEvents.existsById(event.eventId())) {
            log.info("Duplicate event {} ignored", event.eventId());
            return;
        }

        paymentService.markProcessing(event.paymentId());

        // businessKey = paymentId -> you can search the instance by payment in Cockpit.
        // Variables are stored as simple types (String) - avoids Java serialisation in the engine DB.
        runtimeService.startProcessInstanceByKey(
                ProcessConstants.PROCESS_KEY,
                event.paymentId().toString(),
                Map.of(
                        ProcessConstants.VAR_PAYMENT_ID, event.paymentId().toString(),
                        ProcessConstants.VAR_AMOUNT, event.amount().toPlainString() + " " + event.currency(),
                        ProcessConstants.VAR_DEBTOR_IBAN, event.debtorIban()));

        processedEvents.save(new ProcessedEvent(event.eventId(), CONSUMER_NAME));
        log.info("Started payment process for payment {}", event.paymentId());
    }
}
