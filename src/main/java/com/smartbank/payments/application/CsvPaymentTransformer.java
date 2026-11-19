package com.smartbank.payments.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * "Transform" step of an integration platform: turns an external/legacy format into our
 * internal command. Here: a CSV file from a partner system.
 *
 * Expected format (header line required):
 *   reference,debtorIban,creditorIban,amount,currency,remittanceInfo
 *   INV-1001,DE89370400440532013000,DE02120300000000202051,250.00,EUR,Invoice 1001
 *
 * The partner's "reference" becomes the idempotency key -> uploading the same file twice
 * does not create duplicate payments.
 *
 * Simplification: no quoted fields. In production use a CSV library (Apache Commons CSV,
 * OpenCSV) or a standard format like ISO 20022 pain.001 XML.
 */
@Component
public class CsvPaymentTransformer {

    static final String EXPECTED_HEADER = "reference,debtorIban,creditorIban,amount,currency,remittanceInfo";

    /** One parsed line: either a command or an error message for that line number. */
    public record Row(int lineNumber, String reference, NewPayment payment, String error) {

        public boolean isValid() {
            return error == null;
        }
    }

    public List<Row> transform(String csv) {
        List<Row> rows = new ArrayList<>();
        String[] lines = csv.strip().split("\\R");          // \R = any line break (\n, \r\n)
        if (lines.length == 0 || !lines[0].strip().equalsIgnoreCase(EXPECTED_HEADER)) {
            throw new IllegalArgumentException("CSV header must be: " + EXPECTED_HEADER);
        }
        for (int i = 1; i < lines.length; i++) {
            int lineNumber = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            rows.add(parseLine(lineNumber, line));
        }
        return rows;
    }

    private Row parseLine(int lineNumber, String line) {
        String[] f = line.split(",", -1);                   // -1 keeps trailing empty fields
        if (f.length != 6) {
            return new Row(lineNumber, null, null, "Expected 6 fields but got " + f.length);
        }
        String reference = f[0].strip();
        if (reference.isEmpty()) {
            return new Row(lineNumber, null, null, "reference is empty");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(f[3].strip());
        } catch (NumberFormatException e) {
            return new Row(lineNumber, reference, null, "amount is not a number: " + f[3]);
        }
        if (amount.signum() <= 0 || amount.scale() > 2) {
            return new Row(lineNumber, reference, null, "amount must be positive with max. 2 decimals");
        }
        String remittance = f[5].strip();
        NewPayment payment = new NewPayment(f[1].strip(), f[2].strip(), amount, f[4].strip(),
                remittance.isEmpty() ? null : remittance);
        return new Row(lineNumber, reference, payment, null);
    }
}
