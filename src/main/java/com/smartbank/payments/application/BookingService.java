package com.smartbank.payments.application;

import com.smartbank.payments.domain.Account;
import com.smartbank.payments.domain.InsufficientFundsException;
import com.smartbank.payments.domain.Payment;
import com.smartbank.payments.domain.PaymentStatus;
import com.smartbank.payments.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Moves the money: debit debtor, credit creditor, mark payment BOOKED - atomically.
 *
 * Concurrency: Account has @Version (optimistic locking). Two parallel bookings on the same
 * account -> one commit fails with ObjectOptimisticLockingFailureException. We let it propagate:
 * the Camunda job fails and is retried (R3/PT10S in the BPMN), and the retry reads fresh balances.
 *
 * Alternative: pessimistic locking (SELECT ... FOR UPDATE). Safer under high contention on one
 * account, but holds DB locks longer; you must lock accounts in a fixed order (e.g. sorted IBAN)
 * to avoid deadlocks.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final PaymentService paymentService;
    private final AccountRepository accountRepository;

    public BookingService(PaymentService paymentService, AccountRepository accountRepository) {
        this.paymentService = paymentService;
        this.accountRepository = accountRepository;
    }

    /**
     * @throws InsufficientFundsException business error -> rejection path
     *
     * Why noRollbackFor? This method runs INSIDE the Camunda job's transaction (propagation REQUIRED
     * joins it). By default, a RuntimeException leaving a @Transactional method marks the WHOLE
     * shared transaction "rollback-only". The delegate catches the exception and throws a BpmnError
     * so the process continues on the rejection path - but the commit would then still fail with
     * UnexpectedRollbackException and the job would be retried instead of rejected.
     * debit() checks the balance BEFORE changing anything, so nothing is half-done when it throws.
     */
    @Transactional(noRollbackFor = InsufficientFundsException.class)
    public void book(UUID paymentId) {
        Payment payment = paymentService.findRequired(paymentId);
        if (payment.getStatus() == PaymentStatus.BOOKED || payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("Payment {} already booked - skipping (idempotent retry)", paymentId);
            return;
        }

        Account debtor = accountRepository.findById(payment.getDebtorIban())
                .orElseThrow(() -> new IllegalStateException("Debtor account vanished"));
        Account creditor = accountRepository.findById(payment.getCreditorIban())
                .orElseThrow(() -> new IllegalStateException("Creditor account vanished"));

        debtor.debit(payment.getAmount());   // may throw InsufficientFundsException
        creditor.credit(payment.getAmount());
        payment.markBooked();

        log.info("Booked payment {}: {} -> {} ({} {})", paymentId,
                debtor.getIban(), creditor.getIban(), payment.getAmount(), payment.getCurrency());
    }
}
