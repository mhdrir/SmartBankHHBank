package com.smartbank.payments.process;

import com.smartbank.payments.application.PaymentService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Service task "Complete payment": status COMPLETED + PaymentCompleted event into the outbox. */
@Component("completePaymentDelegate")
public class CompletePaymentDelegate implements JavaDelegate {

    private final PaymentService paymentService;

    public CompletePaymentDelegate(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        UUID paymentId = UUID.fromString((String) execution.getVariable(ProcessConstants.VAR_PAYMENT_ID));
        paymentService.markCompleted(paymentId);
    }
}
