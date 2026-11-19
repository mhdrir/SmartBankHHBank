package com.smartbank.payments.api;

import com.smartbank.payments.application.IdempotencyConflictException;
import com.smartbank.payments.application.PaymentNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One place that turns exceptions into HTTP responses.
 *
 * Format: RFC 9457 "Problem Details" (application/problem+json), built into Spring 6:
 *   { "type": "...", "title": "Not Found", "status": 404, "detail": "Payment ... not found" }
 *
 * Extending ResponseEntityExceptionHandler gives standard handling for Spring MVC errors
 * (invalid JSON, @Valid failures -> 400, missing header -> 400, wrong method -> 405).
 * Never return stack traces to clients - they leak internals.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail notFound(PaymentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /** Two parallel requests with the same Idempotency-Key: the DB unique constraint stops the second. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Concurrent request with the same Idempotency-Key - please retry");
    }

    /** Validation of @RequestHeader / @PathVariable constraints (method validation). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** E.g. wrong CSV header. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
