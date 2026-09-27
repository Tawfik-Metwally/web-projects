package io.github.tawfikmetwally.payments.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.SignedJWT;
import io.github.tawfikmetwally.payments.TestcontainersConfiguration;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.repository.IdempotencyRecordRepository;
import io.github.tawfikmetwally.payments.repository.PaymentEventRepository;
import io.github.tawfikmetwally.payments.repository.PaymentRepository;
import io.github.tawfikmetwally.payments.repository.RefundRepository;
import io.github.tawfikmetwally.payments.support.JwtSigningTestSupport;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class JwtAuthenticationIntegrationTests {

    private static final String BASE = "/api/v1/payments";
    private static final String MERCHANT_A = "merchant-a-client";
    private static final String MERCHANT_B = "merchant-b-client";
    private static final JwtSigningTestSupport SIGNING = new JwtSigningTestSupport();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private RefundRepository refunds;

    @Autowired
    private PaymentEventRepository events;

    @Autowired
    private IdempotencyRecordRepository idempotency;

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

        var result = mockMvc.perform(bearer(operation(Route.CREATE), SIGNING.token(MERCHANT_A)))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        paymentId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @ParameterizedTest(name = "{0}, invalid token: {1}")
    @MethodSource("invalidTokenCases")
    void rejectsInvalidTokenOnEveryEndpointWithoutChangingDatabase(Route route, InvalidToken reason) throws Exception {
        String token =
                switch (reason) {
                    case EXPIRED ->
                        SIGNING.token(MERCHANT_A, JwtSigningTestSupport.ISSUER, JwtSigningTestSupport.AUDIENCE, -300);
                    case WRONG_ISSUER ->
                        SIGNING.token(MERCHANT_A, "https://other.example.test", JwtSigningTestSupport.AUDIENCE, 300);
                    case WRONG_AUDIENCE -> SIGNING.token(MERCHANT_A, JwtSigningTestSupport.ISSUER, "another-api", 300);
                };
        // A new key makes accidental acceptance observable as a new write, not a replay.
        assertRejected(
                bearer(operation(route).headers(headers -> headers.set("Idempotency-Key", "invalid-attempt")), token));
    }

    static Stream<Arguments> invalidTokenCases() {
        return Arrays.stream(Route.values())
                .flatMap(route -> Arrays.stream(InvalidToken.values()).map(reason -> Arguments.of(route, reason)));
    }

    @Test
    void rejectsMerchantIdentityChangedAfterSigning() throws Exception {
        String original = SIGNING.tokenWithScopes(MERCHANT_B, "payments:read");
        mockMvc.perform(bearer(operation(Route.GET), original)).andExpect(status().isNotFound());

        String altered = SIGNING.tamperClaim(original, "azp", MERCHANT_A);
        assertTamperedClaim(original, altered, "azp", MERCHANT_A);
        assertRejected(bearer(operation(Route.GET), altered));
    }

    @Test
    void rejectsPermissionAddedAfterSigning() throws Exception {
        String original = SIGNING.tokenWithScopes(MERCHANT_A, "payments:read");
        mockMvc.perform(bearer(operation(Route.CREATE), original))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this operation."))
                .andExpect(jsonPath("$.instance").exists());

        String altered = SIGNING.tamperClaim(original, "scope", "payments:read payments:create");
        assertTamperedClaim(original, altered, "scope", "payments:read payments:create");
        assertRejected(bearer(
                operation(Route.CREATE).headers(headers -> headers.set("Idempotency-Key", "tampered-attempt")),
                altered));
    }

    @Test
    void doesNotReuseAuthenticationAcrossRequestsEvenWithSameSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(bearer(operation(Route.GET).session(session), SIGNING.token(MERCHANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()));
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isNull();

        mockMvc.perform(operation(Route.GET).session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("A valid access token is required."))
                .andExpect(jsonPath("$.instance").exists())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));

        // A later valid token must identify B, not the merchant from the first request.
        mockMvc.perform(bearer(operation(Route.GET).session(session), SIGNING.token(MERCHANT_B)))
                .andExpect(status().isNotFound());
        mockMvc.perform(bearer(operation(Route.GET).session(session), SIGNING.token(MERCHANT_A)))
                .andExpect(status().isOk());
        assertUnchanged();
    }

    private void assertTamperedClaim(String original, String altered, String claim, String expected) throws Exception {
        SignedJWT parsed = SignedJWT.parse(altered);
        assertThat(parsed.getJWTClaimsSet().getStringClaim(claim)).isEqualTo(expected);
        assertThat(parsed.getSignature()).isEqualTo(SignedJWT.parse(original).getSignature());
        assertThat(altered).isNotEqualTo(original);
    }

    private void assertRejected(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("A valid access token is required."))
                .andExpect(jsonPath("$.instance").exists())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
        assertUnchanged();
    }

    private MockHttpServletRequestBuilder operation(Route route) {
        return switch (route) {
            case CREATE ->
                post(BASE)
                        .header("Idempotency-Key", "setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "amount": 10000,
                              "currency": "BRL",
                              "merchantReference": "ORDER-AUTHENTICATION",
                              "paymentMethodToken": "tok_approved"
                            }
                            """);
            case GET -> get(BASE + "/" + paymentId);
            case LIST -> get(BASE);
            case HISTORY -> get(BASE + "/" + paymentId + "/events");
            case REFUND ->
                post(BASE + "/" + paymentId + "/refunds")
                        .header("Idempotency-Key", "refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"CUSTOMER_REQUEST\"}");
        };
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void assertUnchanged() {
        assertThat(payments.count()).isEqualTo(1);
        assertThat(refunds.count()).isZero();
        assertThat(events.count()).isEqualTo(2);
        assertThat(idempotency.count()).isEqualTo(1);
        var payment = payments.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getMerchantId()).isEqualTo(MERCHANT_A);
    }

    enum Route {
        CREATE,
        GET,
        LIST,
        HISTORY,
        REFUND
    }

    enum InvalidToken {
        EXPIRED,
        WRONG_ISSUER,
        WRONG_AUDIENCE
    }
}
