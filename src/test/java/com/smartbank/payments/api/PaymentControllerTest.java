package com.smartbank.payments.api;

import com.smartbank.payments.application.IdempotencyConflictException;
import com.smartbank.payments.application.NewPayment;
import com.smartbank.payments.application.PaymentNotFoundException;
import com.smartbank.payments.application.PaymentService;
import com.smartbank.payments.config.SecurityConfig;
import com.smartbank.payments.config.SmartBankProperties;
import com.smartbank.payments.domain.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Slice" test: @WebMvcTest starts only the web layer (controller, advice, security filter),
 * not the DB, Kafka or Camunda. PaymentService is replaced by a Mockito mock (@MockitoBean).
 *
 * jwt() fakes an authenticated bearer token with the given authorities, so we can test
 * the scope rules (401 / 403) without a real identity provider.
 */
@WebMvcTest(controllers = PaymentController.class,
        properties = {
                "smartbank.payments.manual-review-threshold=10000",
                "smartbank.payments.currency=EUR",
                "smartbank.security.jwt-secret=test-secret-test-secret-test-secret-123"
        })
@Import({SecurityConfig.class, PaymentControllerTest.PropertiesConfig.class})
class PaymentControllerTest {

    /** SecurityConfig needs SmartBankProperties (the JWT secret). */
    @TestConfiguration
    @EnableConfigurationProperties(SmartBankProperties.class)
    static class PropertiesConfig {
    }

    private static final String VALID_BODY = """
            {
              "debtorIban": "DE89370400440532013000",
              "creditorIban": "DE02120300000000202051",
              "amount": 250.00,
              "currency": "EUR",
              "remittanceInfo": "Invoice 42"
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PaymentService paymentService;

    private Payment samplePayment() {
        return new Payment("key-1", "hash", "DE89370400440532013000", "DE02120300000000202051",
                new BigDecimal("250.00"), "EUR", "Invoice 42");
    }

    @Test
    void createReturns202WithLocation() throws Exception {
        Payment payment = samplePayment();
        when(paymentService.createPayment(eq("key-1"), any(NewPayment.class)))
                .thenReturn(new PaymentService.CreationResult(payment, false));

        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "http://localhost/api/v1/payments/" + payment.getId()))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void replayReturns200() throws Exception {
        when(paymentService.createPayment(eq("key-1"), any(NewPayment.class)))
                .thenReturn(new PaymentService.CreationResult(samplePayment(), true));

        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void conflictingReplayReturns422() throws Exception {
        when(paymentService.createPayment(eq("key-1"), any(NewPayment.class)))
                .thenThrow(new IdempotencyConflictException("key-1"));

        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        String negativeAmount = VALID_BODY.replace("250.00", "-5");
        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write")))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(negativeAmount))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readScopeCannotCreate() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:read")))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownPaymentReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.getPayment(id)).thenThrow(new PaymentNotFoundException(id));

        mockMvc.perform(get("/api/v1/payments/" + id).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:read"))))
                .andExpect(status().isNotFound());
    }
}
