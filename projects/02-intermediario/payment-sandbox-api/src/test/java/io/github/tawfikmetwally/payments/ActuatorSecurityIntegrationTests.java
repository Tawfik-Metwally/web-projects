package io.github.tawfikmetwally.payments;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import io.github.tawfikmetwally.payments.observability.TraceContext;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class ActuatorSecurityIntegrationTests {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final JwtSigningTestSupport SIGNING = new JwtSigningTestSupport();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        SIGNING.registerProperties(registry);
    }

    @AfterAll
    static void stopSigningServer() {
        SIGNING.close();
    }

    @Test
    void exposesHealthAndProbesWithoutAuthenticationOrDetails() throws Exception {
        for (String path : new String[] {
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness"
        }) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist())
                    .andExpect(header().string(
                            TraceContext.TRACE_ID_HEADER,
                            matchesPattern(UUID_PATTERN)));
        }
    }

    @Test
    void requiresAuthenticationForMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsMerchantTokenFromOperationalMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + SIGNING.tokenWithScopes(
                                        "merchant-a-client",
                                        "payments:read")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void exposesDiagnosticAndPrometheusMetricsToOperations() throws Exception {
        String token = SIGNING.tokenWithScopes(
                "operations-client",
                "observability:read");

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names", hasItem("jvm.memory.used")))
                .andExpect(jsonPath("$.names", hasItem("http.server.requests")));

        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("http_server_requests_seconds")));
    }

    @Test
    void operationsTokenCannotUseBusinessEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + SIGNING.tokenWithScopes(
                                        "operations-client",
                                        "observability:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void deniesUnapprovedActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/env")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + SIGNING.tokenWithScopes(
                                        "operations-client",
                                        "observability:read")))
                .andExpect(status().isForbidden());
    }
}
