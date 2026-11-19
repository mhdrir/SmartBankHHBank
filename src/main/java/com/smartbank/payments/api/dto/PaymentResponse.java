package com.smartbank.payments.api.dto;

import com.smartbank.payments.domain.Payment;
import com.smartbank.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What the API returns. A separate DTO instead of the JPA entity:
 *  - internal fields (requestHash, version) are not exposed
 *  - entity changes do not silently break the public contract
 *  - no lazy-loading surprises during JSON serialisation
 */
public record PaymentResponse(UUID id,
                              String debtorIban,
                              String creditorIban,
                              BigDecimal amount,
                              String currency,
                              String remittanceInfo,
                              PaymentStatus status,
                              String rejectionReason,
                              Instant createdAt,
                              Instant updatedAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getDebtorIban(), p.getCreditorIban(), p.getAmount(),
                p.getCurrency(), p.getRemittanceInfo(), p.getStatus(), p.getRejectionReason(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
