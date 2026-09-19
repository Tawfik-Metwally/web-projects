package io.github.tawfikmetwally.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class OpenApiDocumentationTests {

    private static final String[] BUSINESS_PATHS = {
            "/api/v1/payments",
            "/api/v1/payments/{paymentId}",
            "/api/v1/payments/{paymentId}/events",
            "/api/v1/payments/{paymentId}/refunds"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void documentsOnlyTheMerchantFacingContract() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        JsonNode document = objectMapper.readTree(json);
        JsonNode paths = document.path("paths");

        assertThat(document.path("openapi").asString()).startsWith("3.");
        assertThat(paths.size()).isEqualTo(BUSINESS_PATHS.length);
        for (String path : BUSINESS_PATHS) {
            assertThat(paths.has(path)).isTrue();
        }
        assertThat(json)
                .doesNotContain("/actuator", "OPERATIONS_CLIENT_SECRET",
                        "MERCHANT_A_CLIENT_SECRET", "client_secret");
    }

    @Test
    void describesBearerSecurityIdempotencyScopesAndErrors() throws Exception {
        JsonNode document = openApiDocument();
        JsonNode bearer = document.path("components")
                .path("securitySchemes")
                .path("bearerAuth");

        assertThat(bearer.path("type").asString()).isEqualTo("http");
        assertThat(bearer.path("scheme").asString()).isEqualTo("bearer");
        assertThat(bearer.path("bearerFormat").asString()).isEqualTo("JWT");
        assertThat(document.path("security").get(0).has("bearerAuth")).isTrue();

        JsonNode createPayment = document.path("paths")
                .path("/api/v1/payments")
                .path("post");
        assertThat(createPayment.path("description").asString())
                .contains("payments:create");
        assertThat(hasRequiredParameter(createPayment, "Idempotency-Key")).isTrue();
        assertThat(createPayment.path("parameters").toString()).doesNotContain("principal");
        for (String status : new String[] {"200", "201", "400", "401", "403", "409", "500"}) {
            assertThat(createPayment.path("responses").has(status)).isTrue();
        }
        assertThat(createPayment.path("responses").path("200").path("headers")
                .has("Idempotency-Replayed")).isTrue();
        assertThat(createPayment.path("responses").path("201").path("headers")
                .has("Location")).isTrue();
        assertThat(createPayment.path("responses").path("201").path("headers")
                .has("X-Trace-Id")).isTrue();
        assertThat(createPayment.path("responses").path("401").path("content")
                .has(MediaType.APPLICATION_PROBLEM_JSON_VALUE)).isTrue();

        JsonNode refund = document.path("paths")
                .path("/api/v1/payments/{paymentId}/refunds")
                .path("post");
        assertThat(refund.path("description").asString()).contains("refunds:create");
        assertThat(hasRequiredParameter(refund, "Idempotency-Key")).isTrue();

        JsonNode schemas = document.path("components").path("schemas");
        assertThat(schemas.has("CreatePaymentRequest")).isTrue();
        assertThat(schemas.has("PaymentResponse")).isTrue();
        assertThat(schemas.has("ProblemDetails")).isTrue();
        assertThat(schemas.has("ValidationProblemDetails")).isTrue();
    }

    @Test
    void servesSwaggerUiAndYamlWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Swagger UI")));

        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk());
    }

    private JsonNode openApiDocument() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private boolean hasRequiredParameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asString())
                    && parameter.path("required").asBoolean()) {
                return true;
            }
        }
        return false;
    }
}
