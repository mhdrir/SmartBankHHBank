package com.smartbank.payments.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests the small state machine inside Payment. */
class PaymentTest {

    private Payment newPayment() {
        return new Payment("key-1", "hash", "DE89370400440532013000", "DE02120300000000202051",
                new BigDecimal("10.00"), "EUR", "test");
    }

    @Test
    void happyPathTransitions() {
        Payment p = newPayment();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        p.markProcessing();
        p.markBooked();
        p.markCompleted();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void cannotCompleteWithoutBooking() {
        Payment p = newPayment();
        p.markProcessing();
        assertThatThrownBy(p::markCompleted).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finalPaymentCannotBeRejected() {
        Payment p = newPayment();
        p.markProcessing();
        p.markBooked();
        p.markCompleted();
        assertThatThrownBy(() -> p.markRejected("too late")).isInstanceOf(IllegalStateException.class);
    }
}
