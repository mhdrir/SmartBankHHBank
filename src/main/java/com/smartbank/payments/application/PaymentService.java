package com.smartbank.payments.application;

import com.smartbank.payments.domain.Payment;
import com.smartbank.payments.domain.PaymentStatus;
import com.smartbank.payments.messaging.PaymentEvent;
import com.smartbank.payments.messaging.Topics;
import com.smartbank.payments.outbox.OutboxWriter;
import com.smartbank.payments.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: one public method = one use case = one transaction.
 *
 * Constructor injection (no @Autowired on fields): dependencies are final, the class can be
 * unit-tested with plain "new PaymentService(mockRepo, mockWriter)".
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OutboxWriter outboxWriter;

    public PaymentService(PaymentRepository paymentRepository, OutboxWriter outboxWriter) {
        this.paymentRepository = paymentRepository;
        this.outboxWriter = outboxWriter;
    }

    /** replayed = true means: this key was seen before, we return the existing payment (HTTP 200 instead of 202). */
    public record CreationResult(Payment payment, boolean replayed) {
    }

    /**
     * Creates a payment idempotently and stores the "PaymentRequested" event in the outbox -
     * both in ONE transaction (all or nothing).
     */
    @Transactional
    public CreationResult createPayment(String idempotencyKey, NewPayment command) {
        NewPayment normalized = normalize(command);
        String hash = RequestHasher.hash(normalized);

        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (existing.get().getRequestHash().equals(hash)) {
                log.info("Idempotent replay for key {}", idempotencyKey);
                return new CreationResult(existing.get(), true);
            }
            throw new IdempotencyConflictException(idempotencyKey);
        }

        Payment payment = new Payment(idempotencyKey, hash,
                normalized.debtorIban(), normalized.creditorIban(),
                normalized.amount(), normalized.currency(), normalized.remittanceInfo());
        paymentRepository.save(payment);

        // Key = debtor IBAN: all payments of one account land in the same partition -> processed in order.
        outboxWriter.write(Topics.PAYMENTS_REQUESTED, payment.getDebtorIban(),
                new PaymentEvent.PaymentRequested(UUID.randomUUID(), payment.getId(),
                        payment.getDebtorIban(), payment.getCreditorIban(),
                        payment.getAmount(), payment.getCurrency(), Instant.now()));

        log.info("Payment {} received ({} {})", payment.getId(), payment.getAmount(), payment.getCurrency());
        return new CreationResult(payment, false);
    }

    /** Read use case for the API. readOnly: hint to Hibernate/DB, no dirty checking -> cheaper. */
    @Transactional(readOnly = true)
    public Payment getPayment(UUID id) {
        return findRequired(id);
    }

    /**
     * Loads a payment inside the CALLER's transaction (no own @Transactional on purpose,
     * so write use cases in other services do not join a read-only boundary).
     */
    public Payment findRequired(UUID id) {
        return paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    @Transactional
    public void markProcessing(UUID id) {
        findRequired(id).markProcessing();
        // No explicit save(): the entity is "managed"; Hibernate flushes changes at commit (dirty checking).
    }

    @Transactional
    public void markPendingReview(UUID id) {
        findRequired(id).markPendingReview();
    }

    @Transactional
    public void markCompleted(UUID id) {
        Payment payment = findRequired(id);
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return; // idempotent: a retried job must not publish the event twice
        }
        payment.markCompleted();
        outboxWriter.write(Topics.PAYMENTS_COMPLETED, payment.getId().toString(),
                new PaymentEvent.PaymentCompleted(UUID.randomUUID(), payment.getId(),
                        payment.getDebtorIban(), payment.getCreditorIban(),
                        payment.getAmount(), payment.getCurrency(), Instant.now()));
    }

    @Transactional
    public void markRejected(UUID id, String reason) {
        Payment payment = findRequired(id);
        if (payment.getStatus() == PaymentStatus.REJECTED) {
            return; // idempotent
        }
        payment.markRejected(reason);
        outboxWriter.write(Topics.PAYMENTS_REJECTED, payment.getId().toString(),
                new PaymentEvent.PaymentRejected(UUID.randomUUID(), payment.getId(), reason, Instant.now()));
        log.info("Payment {} rejected: {}", id, reason);
    }

    /** Upper-case IBANs/currency and remove spaces ("DE89 3704 ..." -> "DE893704..."). */
    private static NewPayment normalize(NewPayment c) {
        return new NewPayment(
                c.debtorIban().replace(" ", "").toUpperCase(Locale.ROOT),
                c.creditorIban().replace(" ", "").toUpperCase(Locale.ROOT),
                c.amount(),
                c.currency().toUpperCase(Locale.ROOT),
                c.remittanceInfo());
    }
}
