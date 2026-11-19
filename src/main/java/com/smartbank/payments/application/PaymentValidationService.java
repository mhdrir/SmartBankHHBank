package com.smartbank.payments.application;

import com.smartbank.payments.config.SmartBankProperties;
import com.smartbank.payments.domain.Account;
import com.smartbank.payments.domain.Payment;
import com.smartbank.payments.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Business validation that needs the database (Bean Validation in the API only checks the format).
 *
 * Returns a result object instead of throwing: the Camunda delegate decides how to react
 * (BpmnError -> rejection path). Keeps this class free of Camunda classes and easy to unit test.
 */
@Service
public class PaymentValidationService {

    private final PaymentService paymentService;
    private final AccountRepository accountRepository;
    private final SmartBankProperties properties;

    public PaymentValidationService(PaymentService paymentService, AccountRepository accountRepository,
                                    SmartBankProperties properties) {
        this.paymentService = paymentService;
        this.accountRepository = accountRepository;
        this.properties = properties;
    }

    public record ValidationResult(boolean valid, String reason, boolean requiresManualReview) {

        static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason, false);
        }

        static ValidationResult ok(boolean requiresManualReview) {
            return new ValidationResult(true, null, requiresManualReview);
        }
    }

    @Transactional
    public ValidationResult validate(UUID paymentId) {
        Payment payment = paymentService.findRequired(paymentId);
        String bankCurrency = properties.payments().currency();

        if (!bankCurrency.equals(payment.getCurrency())) {
            return ValidationResult.invalid("Only " + bankCurrency + " payments are supported");
        }
        if (payment.getDebtorIban().equals(payment.getCreditorIban())) {
            return ValidationResult.invalid("Debtor and creditor account must differ");
        }
        Optional<Account> debtor = accountRepository.findById(payment.getDebtorIban());
        if (debtor.isEmpty()) {
            return ValidationResult.invalid("Unknown debtor account " + payment.getDebtorIban());
        }
        if (accountRepository.findById(payment.getCreditorIban()).isEmpty()) {
            // Simplification: in reality an external creditor IBAN would go to SEPA clearing.
            return ValidationResult.invalid("Unknown creditor account " + payment.getCreditorIban());
        }

        boolean needsReview = payment.getAmount().compareTo(properties.payments().manualReviewThreshold()) >= 0;
        if (needsReview) {
            paymentService.markPendingReview(paymentId);
        }
        return ValidationResult.ok(needsReview);
    }
}
