package com.smartbank.payments.api.dto;

import java.util.List;

/**
 * Result of a CSV import. Partial success is allowed: valid lines are accepted, invalid lines
 * are reported with their line number so the partner can fix and re-send only those.
 */
public record ImportResult(int accepted, int duplicates, List<LineError> errors) {

    public record LineError(int line, String reference, String message) {
    }
}
