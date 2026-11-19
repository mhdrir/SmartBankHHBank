package com.smartbank.payments;

import com.smartbank.payments.domain.PaymentStatus;
import com.smartbank.payments.repository.AccountRepository;
import com.smartbank.payments.repository.PaymentRepository;
import com.jayway.jsonpath.JsonPath;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the whole flow with REAL infrastructure in Docker (Testcontainers):
 * PostgreSQL + Kafka start once for this class; @ServiceConnection injects their URLs into Spring.
 *
 * REST -> DB + outbox -> Kafka -> listener -> Camunda (job executor) -> booking -> outbox -> Kafka
 *
 * Asynchronous, so we use Awaitility: "poll until the status is COMPLETED, max. 30 s".
 * Runs in "mvn verify" (failsafe, *IT). Skipped automatically when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PaymentFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    private static final String ANNA = "DE89370400440532013000";   // 50,000 EUR (seed data)
    private static final String BEN = "DE02120300000000202051";    // 1,000 EUR
    private static final String SHOP = "DE75512108001245126199";   // 0 EUR

    @Autowired
    MockMvc mockMvc;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    AccountRepository accountRepository;
    @Autowired
    TaskService taskService;

    private UUID createPayment(String debtor, String creditor, String amount) throws Exception {
        String body = """
                {"debtorIban":"%s","creditorIban":"%s","amount":%s,"currency":"EUR","remittanceInfo":"IT"}
                """.formatted(debtor, creditor, amount);
        String response = mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private void awaitStatus(UUID paymentId, PaymentStatus expected) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(300)).untilAsserted(() ->
                assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(expected));
    }

    @Test
    void smallPaymentIsBookedAutomatically() throws Exception {
        BigDecimal shopBefore = accountRepository.findById(SHOP).orElseThrow().getBalance();

        UUID id = createPayment(BEN, SHOP, "100.00");

        awaitStatus(id, PaymentStatus.COMPLETED);
        assertThat(accountRepository.findById(SHOP).orElseThrow().getBalance())
                .isEqualByComparingTo(shopBefore.add(new BigDecimal("100.00")));
    }

    @Test
    void highPaymentWaitsForFourEyesReview() throws Exception {
        UUID id = createPayment(ANNA, SHOP, "15000.00");

        awaitStatus(id, PaymentStatus.PENDING_REVIEW);
        Task task = await().atMost(Duration.ofSeconds(30)).until(
                () -> taskService.createTaskQuery().processInstanceBusinessKey(id.toString()).singleResult(),
                t -> t != null);

        taskService.complete(task.getId(), Map.of("approved", true));

        awaitStatus(id, PaymentStatus.COMPLETED);
    }

    @Test
    void insufficientFundsIsRejected() throws Exception {
        UUID id = createPayment(BEN, SHOP, "9999.00");   // Ben has at most 1,000 EUR

        awaitStatus(id, PaymentStatus.REJECTED);
        assertThat(paymentRepository.findById(id).orElseThrow().getRejectionReason())
                .contains("Insufficient funds");
    }
}
