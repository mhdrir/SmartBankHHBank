package com.smartbank.payments.api;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * LOCAL ONLY (@Profile("local")): issues a signed JWT so you can call the API from curl/Swagger
 * without running an identity provider. Never deploy something like this to production.
 *
 *   curl -X POST "http://localhost:8080/dev/token?subject=mehdi"
 */
@RestController
@Profile("local")
public class DevTokenController {

    private final JwtEncoder jwtEncoder;

    public DevTokenController(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    @PostMapping("/dev/token")
    public Map<String, Object> token(@RequestParam(defaultValue = "demo-client") String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("smartbank-local")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("scope", "payments:read payments:write")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return Map.of("access_token", token, "token_type", "Bearer", "expires_in", 3600);
    }
}
