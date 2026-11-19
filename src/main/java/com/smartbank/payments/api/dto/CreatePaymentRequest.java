package com.smartbank.payments.api.dto;

import com.smartbank.payments.application.NewPayment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * JSON body of POST /api/v1/payments. Bean Validation checks the FORMAT (400 Bad Request if wrong);
 * business rules (does the account exist? enough money?) are checked later in the process.
 *
 * IBAN regex: 2 letters country, 2 check digits, up to 30 alphanumerics. (A full check would also
 * verify the mod-97 checksum.)
 */
public record CreatePaymentRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}[0-9]{2}[A-Za-z0-9 ]{11,34}") String debtorIban,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}[0-9]{2}[A-Za-z0-9 ]{11,34}") String creditorIban,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 140) String remittanceInfo) {

    public NewPayment toCommand() {
        return new NewPayment(debtorIban, creditorIban, amount, currency, remittanceInfo);
    }
}
