package com.smartbank.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment order (credit transfer) from a debtor account to a creditor account.
 *
 * idempotencyKey: sent by the client in the "Idempotency-Key" header. It has a UNIQUE
 * constraint in the database, so even two parallel requests with the same key can never
 * create two payments. requestHash lets us tell "same request repeated" (OK, return the
 * existing payment) from "same key, different content" (client bug, reject with 422).
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "debtor_iban", nullable = false, length = 34)
    private String debtorIban;

    @Column(name = "creditor_iban", nullable = false, length = 34)
    private String creditorIban;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "remittance_info", length = 140)   // 140 = SEPA limit for unstructured remittance info
    private String remittanceInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Payment() {
    }

    public Payment(String idempotencyKey, String requestHash, String debtorIban, String creditorIban,
                   BigDecimal amount, String currency, String remittanceInfo) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.debtorIban = debtorIban;
        this.creditorIban = creditorIban;
        this.amount = amount;
        this.currency = currency;
        this.remittanceInfo = remittanceInfo;
        this.status = PaymentStatus.RECEIVED;
    }

    // ---- state transitions (small state machine; illegal transitions fail loudly) ----

    public void markProcessing() {
        transition(PaymentStatus.PROCESSING, PaymentStatus.RECEIVED);
    }

    public void markPendingReview() {
        transition(PaymentStatus.PENDING_REVIEW, PaymentStatus.PROCESSING);
    }

    public void markBooked() {
        transition(PaymentStatus.BOOKED, PaymentStatus.PROCESSING, PaymentStatus.PENDING_REVIEW);
    }

    public void markCompleted() {
        transition(PaymentStatus.COMPLETED, PaymentStatus.BOOKED);
    }

    public void markRejected(String reason) {
        if (status.isFinal()) {
            throw new IllegalStateException("Payment " + id + " is already " + status);
        }
        this.status = PaymentStatus.REJECTED;
        this.rejectionReason = reason;
    }

    private void transition(PaymentStatus target, PaymentStatus... allowedFrom) {
        for (PaymentStatus allowed : allowedFrom) {
            if (status == allowed) {
                status = target;
                return;
            }
        }
        throw new IllegalStateException("Cannot move payment " + id + " from " + status + " to " + target);
    }

    // ---- JPA lifecycle callbacks: timestamps without touching every service method ----

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getDebtorIban() {
        return debtorIban;
    }

    public String getCreditorIban() {
        return creditorIban;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getRemittanceInfo() {
        return remittanceInfo;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
