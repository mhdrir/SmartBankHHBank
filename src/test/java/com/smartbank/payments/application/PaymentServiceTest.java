package com.smartbank.payments.application;

import com.smartbank.payments.domain.Payment;
import com.smartbank.payments.messaging.PaymentEvent;
import com.smartbank.payments.messaging.Topics;
import com.smartbank.payments.outbox.OutboxWriter;
import com.smartbank.payments.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test with Mockito: repository and outbox are mocked, so we test ONLY the logic of
 * PaymentService (idempotency rules, normalisation, event creation).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    OutboxWriter outboxWriter;
    @InjectMocks
    PaymentService paymentService;

    private final NewPayment command = new NewPayment(
            "de89 3704 0044 0532 0130 00", "DE02120300000000202051",
            new BigDecimal("250.00"), "eur", "Invoice 42");

    @Test
    void createsPaymentAndWritesOutboxEvent() {
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        PaymentService.CreationResult result = paymentService.createPayment("key-1", command);

        assertThat(result.replayed()).isFalse();
        Payment payment = result.payment();
        assertThat(payment.getDebtorIban()).isEqualTo("DE89370400440532013000"); // normalised
        assertThat(payment.getCurrency()).isEqualTo("EUR");
        verify(paymentRepository).save(payment);

        ArgumentCaptor<PaymentEvent> event = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(outboxWriter).write(eq(Topics.PAYMENTS_REQUESTED), eq("DE89370400440532013000"), event.capture());
        assertThat(event.getValue()).isInstanceOf(PaymentEvent.PaymentRequested.class);
        assertThat(event.getValue().paymentId()).isEqualTo(payment.getId());
    }

    @Test
    void sameKeySameRequestReturnsExistingPayment() {
        // First call to learn the hash the service computes
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        Payment first = paymentService.createPayment("key-1", command).payment();

        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(first));
        PaymentService.CreationResult replay = paymentService.createPayment("key-1", command);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.payment()).isSameAs(first);
    }

    @Test
    void sameKeyDifferentRequestIsRejected() {
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        Payment first = paymentService.createPayment("key-1", command).payment();

        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(first));
        NewPayment different = new NewPayment(command.debtorIban(), command.creditorIban(),
                new BigDecimal("999.00"), "EUR", "Invoice 42");

        assertThatThrownBy(() -> paymentService.createPayment("key-1", different))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void rejectingTwiceWritesOnlyOneEvent() {
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        Payment payment = paymentService.createPayment("key-2", command).payment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        paymentService.markRejected(payment.getId(), "reason");
        paymentService.markRejected(payment.getId(), "reason");   // e.g. a retried job

        verify(outboxWriter).write(eq(Topics.PAYMENTS_REJECTED), anyString(), any());
        verify(outboxWriter, never()).write(eq(Topics.PAYMENTS_COMPLETED), anyString(), any());
    }
}
