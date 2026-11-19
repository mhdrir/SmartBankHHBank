package com.smartbank.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A bank account. The IBAN is the natural primary key.
 *
 * Two banking rules live here:
 *  1. Money is BigDecimal, never double (0.1 + 0.2 != 0.3 in floating point).
 *  2. @Version = optimistic locking. If two transactions load the same account and both
 *     try to change the balance, the second commit fails with an OptimisticLockException
 *     instead of silently overwriting the first ("lost update"). Camunda then retries the job.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(length = 34)
    private String iban;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Incremented by Hibernate on every update; part of the WHERE clause of the UPDATE. */
    @Version
    private long version;

    /** JPA needs a no-arg constructor; protected so application code cannot misuse it. */
    protected Account() {
    }

    public Account(String iban, String ownerName, BigDecimal balance, String currency) {
        this.iban = Objects.requireNonNull(iban);
        this.ownerName = Objects.requireNonNull(ownerName);
        this.balance = Objects.requireNonNull(balance);
        this.currency = Objects.requireNonNull(currency);
    }

    /**
     * Rich domain model: the rule "balance must not go negative" sits in the entity itself,
     * not scattered across services.
     */
    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {          // compareTo, NOT equals: 10.0 vs 10.00
            throw new InsufficientFundsException(iban);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    public String getIban() {
        return iban;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public long getVersion() {
        return version;
    }
}
