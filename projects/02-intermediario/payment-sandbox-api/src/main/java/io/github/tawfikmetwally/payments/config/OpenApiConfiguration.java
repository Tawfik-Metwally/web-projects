package io.github.tawfikmetwally.payments.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfiguration {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI paymentSandboxOpenApi() {
        var bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste only a short-lived access token issued by the "
                        + "payment-sandbox Keycloak realm.");

        return new OpenAPI()
                .info(new Info()
                        .title("Payment Sandbox API")
                        .version("v1")
                        .description("Simulated payment API for learning and portfolio "
                                + "demonstration. Amounts use minor units, only BRL is "
                                + "accepted, and real payment data must never be used."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    OpenApiCustomizer traceIdResponseHeader() {
        return openApi -> openApi.getPaths().values().stream()
                .flatMap(path -> path.readOperations().stream())
                .flatMap(operation -> operation.getResponses().values().stream())
                .forEach(response -> response.addHeaderObject(
                        "X-Trace-Id",
                        new Header()
                                .description("Correlation ID generated for this request.")
                                .schema(new StringSchema().format("uuid"))));
    }
}
