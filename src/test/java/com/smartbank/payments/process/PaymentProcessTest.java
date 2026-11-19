package com.smartbank.payments.process;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.junit5.ProcessEngineExtension;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.withVariables;

/**
 * Tests the BPMN MODEL itself (gateways, boundary events, user task) - without Spring, DB or Kafka.
 * The Java delegates are replaced by small lambdas via Mocks.register(...).
 *
 * Because the service tasks are asyncBefore, the engine stops in front of them and creates a JOB.
 * The job executor is off in the test, so we run each job explicitly with execute(job()).
 */
@ExtendWith(ProcessEngineExtension.class)
@Deployment(resources = "bpmn/payment-process.bpmn")
class PaymentProcessTest {

    private static final JavaDelegate NOOP = execution -> { };

    /** What the reject task "saw" - lets us check the variables set by the error boundary events. */
    private final AtomicReference<Object> rejectReason = new AtomicReference<>();
    private final AtomicReference<Object> rejectCode = new AtomicReference<>();

    @BeforeEach
    void registerDefaultMocks() {
        Mocks.register("validatePaymentDelegate",
                (JavaDelegate) e -> e.setVariable(ProcessConstants.VAR_REQUIRES_MANUAL_REVIEW, false));
        Mocks.register("bookPaymentDelegate", NOOP);
        Mocks.register("completePaymentDelegate", NOOP);
        Mocks.register("rejectPaymentDelegate", (JavaDelegate) e -> {
            rejectReason.set(e.getVariable(ProcessConstants.VAR_REJECTION_REASON));
            rejectCode.set(e.getVariable(ProcessConstants.VAR_REJECTION_CODE));
        });
    }

    @AfterEach
    void reset() {
        Mocks.reset();
    }

    private ProcessInstance start() {
        return runtimeService().startProcessInstanceByKey(ProcessConstants.PROCESS_KEY, "payment-1",
                Map.of(ProcessConstants.VAR_PAYMENT_ID, "11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void happyPathWithoutReview() {
        ProcessInstance pi = start();
        assertThat(pi).isWaitingAt("Task_ValidatePayment");   // async job before validation

        execute(job());                                         // validate
        assertThat(pi).isWaitingAt("Task_BookPayment");        // async job before booking

        execute(job());                                         // book + complete
        assertThat(pi).isEnded().hasPassed("Task_CompletePayment", "EndEvent_Completed");
    }

    @Test
    void highAmountNeedsApprovedReview() {
        Mocks.register("validatePaymentDelegate",
                (JavaDelegate) e -> e.setVariable(ProcessConstants.VAR_REQUIRES_MANUAL_REVIEW, true));
        ProcessInstance pi = start();
        execute(job());

        assertThat(pi).isWaitingAt("Task_ManualReview");
        assertThat(task()).hasCandidateGroup("payment-reviewers");

        complete(task(), withVariables(ProcessConstants.VAR_APPROVED, true));
        execute(job());

        assertThat(pi).isEnded().hasPassed("Task_ManualReview", "EndEvent_Completed");
    }

    @Test
    void declinedReviewRejectsPayment() {
        Mocks.register("validatePaymentDelegate",
                (JavaDelegate) e -> e.setVariable(ProcessConstants.VAR_REQUIRES_MANUAL_REVIEW, true));
        ProcessInstance pi = start();
        execute(job());

        complete(task(), withVariables(ProcessConstants.VAR_APPROVED, false));

        assertThat(pi).isEnded().hasPassed("Task_RejectPayment", "EndEvent_Rejected")
                .hasNotPassed("Task_BookPayment");
    }

    @Test
    void validationErrorGoesToRejection() {
        Mocks.register("validatePaymentDelegate", (JavaDelegate) e -> {
            throw new BpmnError(ProcessConstants.ERROR_VALIDATION_FAILED, "Unknown debtor account");
        });
        ProcessInstance pi = start();
        execute(job());

        assertThat(pi).isEnded().hasPassed("Boundary_ValidationFailed", "Task_RejectPayment");
        // the error boundary event copied the BpmnError message into the variable "rejectionReason"
        org.assertj.core.api.Assertions.assertThat(rejectReason.get()).isEqualTo("Unknown debtor account");
    }

    @Test
    void insufficientFundsGoesToRejection() {
        Mocks.register("bookPaymentDelegate", (JavaDelegate) e -> {
            throw new BpmnError(ProcessConstants.ERROR_INSUFFICIENT_FUNDS, "Insufficient funds");
        });
        ProcessInstance pi = start();
        execute(job());
        execute(job());

        assertThat(pi).isEnded().hasPassed("Boundary_BookingFailed", "EndEvent_Rejected")
                .hasNotPassed("Task_CompletePayment");
        org.assertj.core.api.Assertions.assertThat(rejectCode.get()).isEqualTo(ProcessConstants.ERROR_INSUFFICIENT_FUNDS);
    }
}
