package com.smartbank.payments.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Type-safe configuration, bound from the "smartbank.*" keys in application.yml.
 *
 * Why a record + @Validated instead of many @Value fields?
 *  - all settings in one place, immutable, easy to test
 *  - the app refuses to start if a value is missing or invalid (fail fast)
 */
@Validated
@ConfigurationProperties(prefix = "smartbank")
public record SmartBankProperties(
        @NotNull @Valid Payments payments,     // @Valid: also validate the fields of the nested record
        @NotNull @Valid Security security) {

    /**
     * @param manualReviewThreshold payments with amount >= this value need a four-eyes review
     * @param currency              the only currency this demo bank books (EUR)
     */
    public record Payments(@NotNull @Positive BigDecimal manualReviewThreshold,
                           @NotBlank String currency) {
    }

    /**
     * @param jwtSecret shared HMAC secret used to verify JWTs locally (min. 32 characters).
     *                  In production you would verify tokens from an identity provider
     *                  (Keycloak, AWS Cognito, Azure AD) via issuer-uri instead.
     */
    public record Security(@NotBlank String jwtSecret) {
    }
}
