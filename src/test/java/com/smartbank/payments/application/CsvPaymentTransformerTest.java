package com.smartbank.payments.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvPaymentTransformerTest {

    private final CsvPaymentTransformer transformer = new CsvPaymentTransformer();

    @Test
    void parsesValidAndInvalidLines() {
        String csv = """
                reference,debtorIban,creditorIban,amount,currency,remittanceInfo
                INV-1,DE89370400440532013000,DE02120300000000202051,250.00,EUR,Invoice 1
                INV-2,DE89370400440532013000,DE02120300000000202051,abc,EUR,Invoice 2
                INV-3,DE89370400440532013000,DE02120300000000202051,10.999,EUR,
                """;

        List<CsvPaymentTransformer.Row> rows = transformer.transform(csv);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).isValid()).isTrue();
        assertThat(rows.get(0).payment().amount()).isEqualByComparingTo("250.00");
        assertThat(rows.get(1).error()).contains("not a number");
        assertThat(rows.get(1).lineNumber()).isEqualTo(3);
        assertThat(rows.get(2).error()).contains("max. 2 decimals");
    }

    @Test
    void rejectsUnknownHeader() {
        assertThatThrownBy(() -> transformer.transform("a,b,c\n1,2,3"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
