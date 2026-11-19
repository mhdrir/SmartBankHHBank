package com.smartbank.payments.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 description, generated from the controllers by springdoc.
 * JSON: /v3/api-docs   UI: /swagger-ui.html  (with an "Authorize" button for the bearer token)
 *
 * "API first" alternative: write the openapi.yaml first and generate the controller interfaces
 * (openapi-generator). Common when external partners (e.g. PSD2 TPPs) depend on the contract.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI smartBankOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartBank Payments API")
                        .version("v1")
                        .description("Credit transfers, processed asynchronously via Kafka and Camunda."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
