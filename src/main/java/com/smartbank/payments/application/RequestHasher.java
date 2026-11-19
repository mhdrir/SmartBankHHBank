package com.smartbank.payments.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Creates a SHA-256 fingerprint of a payment request.
 *
 * Used for idempotency: same Idempotency-Key + same hash  -> harmless retry, return existing payment.
 *                       same Idempotency-Key + other hash -> client error (422).
 *
 * amount.stripTrailingZeros() makes 100, 100.0 and 100.00 produce the same hash.
 */
public final class RequestHasher {

    private RequestHasher() {
    }

    public static String hash(NewPayment p) {
        String canonical = String.join("|",
                p.debtorIban(),
                p.creditorIban(),
                p.amount().stripTrailingZeros().toPlainString(),
                p.currency(),
                Objects.requireNonNullElse(p.remittanceInfo(), ""));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e); // never happens on a standard JVM
        }
    }
}
