package com.smartbank.payments.process;

import com.smartbank.payments.application.BookingService;
import com.smartbank.payments.domain.InsufficientFundsException;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Service task "Book payment".
 *
 * Two kinds of failure, handled differently - a classic interview question:
 *  - Business error (insufficient funds): retrying will not help -> BpmnError -> rejection path.
 *  - Technical error (DB down, optimistic lock conflict): retrying may help -> let the exception
 *    escape. Because the task is "asyncBefore", it runs as a job; Camunda rolls back the transaction
 *    and retries it according to failedJobRetryTimeCycle (R3/PT10S = 3 times, every 10 seconds).
 *    After the last retry an INCIDENT is created and visible in Cockpit for an operator.
 */
@Component("bookPaymentDelegate")
public class BookPaymentDelegate implements JavaDelegate {

    private final BookingService bookingService;

    public BookPaymentDelegate(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        UUID paymentId = UUID.fromString((String) execution.getVariable(ProcessConstants.VAR_PAYMENT_ID));
        try {
            bookingService.book(paymentId);
        } catch (InsufficientFundsException e) {
            throw new BpmnError(ProcessConstants.ERROR_INSUFFICIENT_FUNDS, e.getMessage());
        }
    }
}
