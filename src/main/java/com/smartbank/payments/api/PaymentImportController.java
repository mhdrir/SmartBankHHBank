package com.smartbank.payments.api;

import com.smartbank.payments.api.dto.ImportResult;
import com.smartbank.payments.application.CsvPaymentTransformer;
import com.smartbank.payments.application.CsvPaymentTransformer.Row;
import com.smartbank.payments.application.IdempotencyConflictException;
import com.smartbank.payments.application.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch import from a partner system (CSV) - the "ingest + transform" side of an integration platform.
 *
 * Each line is created in its own transaction (one createPayment call per line), so one bad line
 * does not roll back the whole file. For very large files you would stream the input, process in
 * chunks (Spring Batch) and return a job id instead of a synchronous result.
 */
@RestController
@RequestMapping("/api/v1/payments/import")
public class PaymentImportController {

    private final CsvPaymentTransformer transformer;
    private final PaymentService paymentService;

    public PaymentImportController(CsvPaymentTransformer transformer, PaymentService paymentService) {
        this.transformer = transformer;
        this.paymentService = paymentService;
    }

    @Operation(summary = "Import payments from CSV (text/csv)")
    @PostMapping(consumes = "text/csv")
    public ImportResult importCsv(@RequestBody String csv) {
        int accepted = 0;
        int duplicates = 0;
        List<ImportResult.LineError> errors = new ArrayList<>();

        for (Row row : transformer.transform(csv)) {
            if (!row.isValid()) {
                errors.add(new ImportResult.LineError(row.lineNumber(), row.reference(), row.error()));
                continue;
            }
            try {
                // Partner reference as idempotency key: re-uploading the same file is harmless.
                var result = paymentService.createPayment("csv-" + row.reference(), row.payment());
                if (result.replayed()) {
                    duplicates++;
                } else {
                    accepted++;
                }
            } catch (IdempotencyConflictException | IllegalArgumentException e) {
                errors.add(new ImportResult.LineError(row.lineNumber(), row.reference(), e.getMessage()));
            }
        }
        return new ImportResult(accepted, duplicates, errors);
    }
}
