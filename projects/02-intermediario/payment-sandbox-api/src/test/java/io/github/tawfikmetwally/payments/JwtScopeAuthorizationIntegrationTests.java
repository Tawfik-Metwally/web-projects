package io.github.tawfikmetwally.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.repository.IdempotencyRecordJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;
import io.github.tawfikmetwally.payments.repository.RefundJpaRepository;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class JwtScopeAuthorizationIntegrationTests {

    private static final String MERCHANT = "merchant-a-client";
    private static final String BASE = "/api/v1/payments";
    private static final String ALL_SCOPES = "payments:create payments:read refunds:create";
    private static final JwtSigningTestSupport SIGNING = new JwtSigningTestSupport();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PaymentJpaRepository payments;
    @Autowired
    private RefundJpaRepository refunds;
    @Autowired
    private PaymentEventJpaRepository events;
    @Autowired
    private IdempotencyRecordJpaRepository idempotency;

    private UUID paymentId;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        SIGNING.registerProperties(registry);
    }

    @AfterAll
    static void stopSigningServer() {
        SIGNING.close();
    }

    @BeforeEach
    void prepareOwnedPayment() throws Exception {
        idempotency.deleteAllInBatch();
        refunds.deleteAllInBatch();
        events.deleteAllInBatch();
        payments.deleteAllInBatch();

        var result = mockMvc.perform(bearer(operation(Route.CREATE, "setup"),
                        SIGNING.tokenWithScopes(MERCHANT, "payments:create")))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        paymentId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @ParameterizedTest(name = "{0}, scopes=[{1}], allowed={2}")
    @MethodSource("scopeCases")
    void enforcesRequiredScopeForEveryEndpoint(Route route, String scopes, boolean allowed)
            throws Exception {
        var result = mockMvc.perform(bearer(operation(route, "operation"),
                SIGNING.tokenWithScopes(MERCHANT, scopes)));

        if (!allowed) {
            result.andExpect(status().isForbidden())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                            containsString("insufficient_scope")));
            assertUnchanged();
        } else {
            result.andExpect(status().is(route.successStatus));
            switch (route) {
                case CREATE -> assertCounts(2, 0, 4, 2);
                case REFUND -> {
                    assertCounts(1, 1, 3, 2);
                    assertThat(payments.findById(paymentId).orElseThrow().getStatus())
                            .isEqualTo(PaymentStatus.REFUNDED);
                }
                default -> assertUnchanged();
            }
        }
    }

    static Stream<Arguments> scopeCases() {
        return Arrays.stream(Route.values()).flatMap(route ->
                Stream.of(null, "", "payments:create", "payments:read", "refunds:create",
                                ALL_SCOPES, "payments:read-extra")
                        .map(scopes -> Arguments.of(route, scopes,
                                scopes != null && Arrays.asList(scopes.split(" "))
                                        .contains(route.requiredScope))));
    }

    @ParameterizedTest(name = "{0}, invalid token=[{1}]")
    @MethodSource("unauthenticatedCases")
    void requiresValidAuthenticationForEveryEndpoint(Route route, String token) throws Exception {
        var request = operation(route, "unauthenticated");
        if (!token.isEmpty()) {
            bearer(request, token);
        }
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
        assertUnchanged();
    }

    static Stream<Arguments> unauthenticatedCases() {
        return Arrays.stream(Route.values()).flatMap(route ->
                Stream.of("", "not-a-jwt").map(token -> Arguments.of(route, token)));
    }

    @ParameterizedTest
    @MethodSource("unconfiguredRoutes")
    void deniesUnconfiguredApiRoutesEvenWithAllScopes(HttpMethod method, String path)
            throws Exception {
        mockMvc.perform(bearer(request(method, path), SIGNING.token(MERCHANT)))
                .andExpect(status().isForbidden());
        assertUnchanged();
    }

    static Stream<Arguments> unconfiguredRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/unconfigured"),
                Arguments.of(HttpMethod.PUT, BASE + "/550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void deniesMissingScopeBeforeParsingRequestBody() throws Exception {
        mockMvc.perform(bearer(operation(Route.CREATE, "malformed").content("{broken"),
                        SIGNING.tokenWithScopes(MERCHANT, "payments:read")))
                .andExpect(status().isForbidden());
        assertUnchanged();
    }

    @Test
    void deniesRefundBeforeCheckingOwnershipWhenScopeIsMissing() throws Exception {
        // Mirrors merchant B's configured scopes, but not a live Keycloak token.
        mockMvc.perform(bearer(operation(Route.REFUND, "outsider"),
                        SIGNING.tokenWithScopes("merchant-b-client",
                                "payments:create payments:read")))
                .andExpect(status().isForbidden());
        assertUnchanged();
    }

    private MockHttpServletRequestBuilder operation(Route route, String key) {
        String path = BASE + route.suffix.replace("{id}", String.valueOf(paymentId));
        var request = request(route.method, path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key);
        if (route == Route.CREATE) {
            request.content("""
                    {
                      "amount": 10000,
                      "currency": "BRL",
                      "merchantReference": "ORDER-SCOPE",
                      "paymentMethodToken": "tok_approved"
                    }
                    """);
        } else if (route == Route.REFUND) {
            request.content("{\"reason\":\"CUSTOMER_REQUEST\"}");
        }
        return request;
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void assertUnchanged() {
        assertCounts(1, 0, 2, 1);
        assertThat(payments.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.APPROVED);
    }

    private void assertCounts(long paymentCount, long refundCount, long eventCount, long recordCount) {
        assertThat(payments.count()).isEqualTo(paymentCount);
        assertThat(refunds.count()).isEqualTo(refundCount);
        assertThat(events.count()).isEqualTo(eventCount);
        assertThat(idempotency.count()).isEqualTo(recordCount);
    }

    enum Route {
        CREATE(HttpMethod.POST, "", "payments:create", 201),
        GET(HttpMethod.GET, "/{id}", "payments:read", 200),
        LIST(HttpMethod.GET, "", "payments:read", 200),
        HISTORY(HttpMethod.GET, "/{id}/events", "payments:read", 200),
        REFUND(HttpMethod.POST, "/{id}/refunds", "refunds:create", 201);

        final HttpMethod method;
        final String suffix;
        final String requiredScope;
        final int successStatus;

        Route(HttpMethod method, String suffix, String requiredScope, int successStatus) {
            this.method = method;
            this.suffix = suffix;
            this.requiredScope = requiredScope;
            this.successStatus = successStatus;
        }
    }
}
