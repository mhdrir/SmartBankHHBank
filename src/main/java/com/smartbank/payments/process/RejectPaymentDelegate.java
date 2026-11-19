package com.smartbank.payments.process;

import com.smartbank.payments.application.PaymentService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Service task "Reject payment". Reached from three places:
 *  1. error boundary on "Validate payment"  (rejectionReason set by the BpmnError message)
 *  2. error boundary on "Book payment"      (e.g. insufficient funds)
 *  3. reviewer declined in the four-eyes user task (no reason variable -> default text)
 */
@Component("rejectPaymentDelegate")
public class RejectPaymentDelegate implements JavaDelegate {

    static final String DEFAULT_REASON = "Declined in manual review";

    private final PaymentService paymentService;

    public RejectPaymentDelegate(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        UUID paymentId = UUID.fromString((String) execution.getVariable(ProcessConstants.VAR_PAYMENT_ID));
        Object reason = execution.getVariable(ProcessConstants.VAR_REJECTION_REASON);
        paymentService.markRejected(paymentId, reason != null ? reason.toString() : DEFAULT_REASON);
    }
}
