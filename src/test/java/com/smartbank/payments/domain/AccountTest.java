package com.smartbank.payments.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit test: no Spring, no DB -> runs in milliseconds.
 * The domain rules (balance never negative, positive amounts) are tested where they live.
 */
class AccountTest {

    private Account account() {
        return new Account("DE89370400440532013000", "Anna", new BigDecimal("100.00"), "EUR");
    }

    @Test
    void debitReducesBalance() {
        Account account = account();
        account.debit(new BigDecimal("30.50"));
        assertThat(account.getBalance()).isEqualByComparingTo("69.50");
    }

    @Test
    void debitOfExactBalanceIsAllowed() {
        Account account = account();
        account.debit(new BigDecimal("100"));          // 100 vs 100.00 -> compareTo, not equals
        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void debitMoreThanBalanceThrowsAndChangesNothing() {
        Account account = account();
        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void negativeOrZeroAmountsAreRejected() {
        Account account = account();
        assertThatThrownBy(() -> account.credit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }
}
