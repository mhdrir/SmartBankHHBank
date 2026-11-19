package com.smartbank.payments.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * The API is an OAuth2 "resource server": every call needs "Authorization: Bearer <JWT>".
 *
 * Authorisation by scope (OAuth2 standard claim "scope"; Spring turns each into "SCOPE_x"):
 *   POST /api/v1/payments/**  -> SCOPE_payments:write
 *   GET  /api/v1/payments/**  -> SCOPE_payments:read
 *
 * Local/demo: tokens are signed with a shared HMAC secret (HS256) and can be created via
 * POST /dev/token (only in profile "local"). Production: remove the secret and set
 * spring.security.oauth2.resourceserver.jwt.issuer-uri to the identity provider; the public keys
 * are then fetched from its JWKS endpoint (asymmetric RS256, no shared secret).
 *
 * This is also the model of PSD2 / open banking: a third party (TPP) gets a token with limited
 * scopes (account information vs. payment initiation) after the customer gave consent.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                // Stateless REST API with bearer tokens: no cookies -> CSRF protection not needed.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/dev/token").permitAll()
                        // Camunda web apps (Cockpit, Tasklist) have their own login (demo/demo).
                        .requestMatchers("/camunda/**", "/").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/**").hasAuthority("SCOPE_payments:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/**").hasAuthority("SCOPE_payments:read")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    SecretKey jwtSecretKey(SmartBankProperties properties) {
        byte[] bytes = properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("smartbank.security.jwt-secret must have at least 32 bytes for HS256");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).build();   // validates signature + exp
    }

    /** Only used by the local DevTokenController to issue test tokens. */
    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }
}
