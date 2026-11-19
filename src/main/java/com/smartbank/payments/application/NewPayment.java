package com.smartbank.payments.application;

import java.math.BigDecimal;

/**
 * Command object of the application layer.
 *
 * Why not reuse the REST DTO? Layers stay independent: the API (JSON, validation annotations)
 * can change without touching business code, and the CSV import creates the same command
 * from a completely different input format.
 */
public record NewPayment(String debtorIban,
                         String creditorIban,
                         BigDecimal amount,
                         String currency,
                         String remittanceInfo) {
}
