package com.smartbank.payments.process;

import com.smartbank.payments.application.PaymentValidationService;
import com.smartbank.payments.application.PaymentValidationService.ValidationResult;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Service task "Validate payment".
 *
 * Camunda 7: the BPMN references this bean by name: camunda:delegateExpression="${validatePaymentDelegate}".
 * The delegate is a thin adapter: read variables -> call the application service -> write variables.
 * No business logic here, so the logic can be unit-tested without a process engine.
 *
 * (Camunda 8 equivalent: a @JobWorker(type = "validate-payment") method; the engine runs remotely
 * and workers fetch jobs over gRPC.)
 */
@Component("validatePaymentDelegate")
public class ValidatePaymentDelegate implements JavaDelegate {

    private final PaymentValidationService validationService;

    public ValidatePaymentDelegate(PaymentValidationService validationService) {
        this.validationService = validationService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        UUID paymentId = UUID.fromString((String) execution.getVariable(ProcessConstants.VAR_PAYMENT_ID));

        ValidationResult result = validationService.validate(paymentId);
        if (!result.valid()) {
            // Caught by the error boundary event on this task -> "Reject payment".
            throw new BpmnError(ProcessConstants.ERROR_VALIDATION_FAILED, result.reason());
        }
        execution.setVariable(ProcessConstants.VAR_REQUIRES_MANUAL_REVIEW, result.requiresManualReview());
    }
}
