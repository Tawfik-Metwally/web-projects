package io.github.tawfikmetwally.payments.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.ObjectMapper;

import io.github.tawfikmetwally.payments.controller.PaymentController;
import io.github.tawfikmetwally.payments.domain.Money;
import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.observability.TraceContext;
import io.github.tawfikmetwally.payments.service.CreatePaymentCommand;
import io.github.tawfikmetwally.payments.service.CreatePaymentResult;
import io.github.tawfikmetwally.payments.service.CreatePaymentService;
import io.github.tawfikmetwally.payments.service.GetPaymentService;
import io.github.tawfikmetwally.payments.service.ListPaymentsService;

@Import(SecurityConfiguration.class)
@WebMvcTest(PaymentController.class)
class JwtSecurityConfigurationTests {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String MERCHANT_ID = "merchant-a-client";
    private static final String IDEMPOTENCY_KEY = "idem-jwt-123";
    private static final String VALID_TOKEN = "valid-token";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final UUID PAYMENT_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant NOW = Instant.parse("2026-09-14T18:00:00Z");
    private static final Currency BRL = Currency.getInstance("BRL");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CreatePaymentService createPaymentService;

    @MockitoBean
    private GetPaymentService getPaymentService;

    @MockitoBean
    private ListPaymentsService listPaymentsService;

    @Test
    void rejectsRequestWithoutBearerTokenBeforeCallingService()
            throws Exception {
        var result = mockMvc.perform(validRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("A valid access token is required."))
                .andExpect(header().string(TraceContext.TRACE_ID_HEADER,
                        org.hamcrest.Matchers.matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.traceId").value(
                        org.hamcrest.Matchers.matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.instance").exists())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andReturn();

        String headerTraceId = result.getResponse().getHeader(TraceContext.TRACE_ID_HEADER);
        String bodyTraceId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path(TraceContext.TRACE_ID).asString();
        org.assertj.core.api.Assertions.assertThat(bodyTraceId).isEqualTo(headerTraceId);

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void ignoresRemovedDemoMerchantHeader() throws Exception {
        mockMvc.perform(validRequest()
                        .header("X-Demo-Merchant-Id", MERCHANT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("A valid access token is required."))
                .andExpect(jsonPath("$.instance").exists());

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void rejectsTokenThatJwtDecoderCannotValidate() throws Exception {
        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new BadJwtException("invalid token"));

        mockMvc.perform(withBearer(validRequest(), "invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("A valid access token is required."))
                .andExpect(jsonPath("$.instance").exists())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void mapsAuthorizedPartyClaimToMerchantPrincipal() throws Exception {
        when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(validJwt());
        CreatePaymentCommand expectedCommand = new CreatePaymentCommand(
                MERCHANT_ID,
                IDEMPOTENCY_KEY,
                10_000,
                BRL,
                "ORDER-JWT-123",
                "tok_approved");
        Payment payment = payment();
        when(createPaymentService.create(expectedCommand))
                .thenReturn(new CreatePaymentResult(payment, false));

        mockMvc.perform(withBearer(validRequest(), VALID_TOKEN))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        ENDPOINT + "/" + PAYMENT_ID));

        verify(createPaymentService).create(expectedCommand);
        verifyNoMoreInteractions(createPaymentService);
    }

    @Test
    void rejectsMissingMerchantClaimBeforeCallingService() throws Exception {
        Jwt token = Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "RS256").subject("service-account").build();
        when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(token);
        mockMvc.perform(withBearer(validRequest(), VALID_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("A valid access token is required."))
                .andExpect(jsonPath("$.instance").exists());
        verifyNoInteractions(createPaymentService);
    }

    @Test
    void mapsAuthorizedPartyClaimForPaymentLookup() throws Exception {
        when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(validJwt());
        when(getPaymentService.getById(PAYMENT_ID, MERCHANT_ID))
                .thenReturn(payment());

        mockMvc.perform(withBearer(
                        get(ENDPOINT + "/" + PAYMENT_ID)
                                .accept(MediaType.APPLICATION_JSON),
                        VALID_TOKEN))
                .andExpect(status().isOk());

        verify(getPaymentService).getById(PAYMENT_ID, MERCHANT_ID);
        verifyNoMoreInteractions(getPaymentService);
    }

    @ParameterizedTest
    @MethodSource("invalidMerchantClaims")
    void rejectsMalformedMerchantClaimBeforeCallingServices(Object merchant) throws Exception {
        Jwt token = Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "RS256").subject("internal-service-account")
                .claim("azp", merchant).build();
        when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(token);
        mockMvc.perform(withBearer(validRequest(), VALID_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("A valid access token is required."))
                .andExpect(jsonPath("$.instance").exists());
        verifyNoInteractions(createPaymentService, getPaymentService, listPaymentsService);
    }


    @ParameterizedTest
    @ValueSource(strings = { "text/html", "*/*" })
    void returnsProblemForMissingTokenRegardlessOfRequestedMediaType(String accept)
            throws Exception {
        mockMvc.perform(validRequest().accept(accept))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("error="))))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value(ENDPOINT));
        verifyNoInteractions(jwtDecoder, createPaymentService);
    }

    @Test
    void hidesDecoderDiagnosticsAndQueryValuesFromResponse() throws Exception {
        String diagnostic = "private-decoder-diagnostic";
        when(jwtDecoder.decode("private-token"))
                .thenThrow(new BadJwtException(diagnostic));

        mockMvc.perform(withBearer(validRequest().queryParam("secret", "private-query"),
                        "private-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        org.hamcrest.Matchers.containsString("error=\"invalid_token\"")))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-"))))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("error_description"))))
                .andExpect(jsonPath("$.instance").value(ENDPOINT))
                .andExpect(jsonPath("$.detail").value("A valid access token is required."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-"))));
        verifyNoInteractions(createPaymentService);
    }

    @Test
    void rejectsMalformedBearerBeforeCallingDecoder() throws Exception {
        mockMvc.perform(withBearer(validRequest(), "bad token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        org.hamcrest.Matchers.containsString("error=\"invalid_token\"")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(jwtDecoder, createPaymentService);
    }

    @Test
    void returnsProblemWhenValidTokenLacksRequiredScope() throws Exception {
        Jwt token = Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "RS256")
                .subject("service-account")
                .claim("azp", MERCHANT_ID)
                .claim("scope", "payments:read")
                .build();
        when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(token);

        mockMvc.perform(withBearer(validRequest(), VALID_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        org.hamcrest.Matchers.containsString("insufficient_scope")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(
                        "You do not have permission to perform this operation."))
                .andExpect(header().string(TraceContext.TRACE_ID_HEADER,
                        org.hamcrest.Matchers.matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.traceId").value(
                        org.hamcrest.Matchers.matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.instance").value(ENDPOINT))
                .andExpect(jsonPath("$.merchantId").doesNotExist());
        verifyNoInteractions(createPaymentService);
    }

    @Test
    void preservesBadRequestForInvalidAuthenticationRequest() throws Exception {
        var request = new MockHttpServletRequest("POST", ENDPOINT);
        var response = new MockHttpServletResponse();
        request.setAttribute(TraceContext.TRACE_ID_REQUEST_ATTRIBUTE, "trace-security-test");
        var exception = new OAuth2AuthenticationException(new BearerTokenError(
                "invalid_request", HttpStatus.BAD_REQUEST, "private-diagnostic", null));

        new ProblemAuthenticationEntryPoint(objectMapper).commence(request, response, exception);

        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer")
                .contains("error=\"invalid_request\"")
                .doesNotContain("private-diagnostic", "error_description");
        var body = objectMapper.readTree(response.getContentAsString());
        org.assertj.core.api.Assertions.assertThat(body.path("status").asInt()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(body.path("detail").asString())
                .isEqualTo("The authentication request is invalid.");
        org.assertj.core.api.Assertions.assertThat(body.path("traceId").asString())
                .isEqualTo("trace-security-test");
        org.assertj.core.api.Assertions.assertThat(response.getContentAsString())
                .doesNotContain("private-diagnostic");
    }

    @Test
    void leavesAlreadyCommittedResponsesUntouched() throws Exception {
        var request = new MockHttpServletRequest("GET", ENDPOINT);
        var response = new MockHttpServletResponse();
        response.setStatus(202);
        response.getWriter().write("already sent");
        response.flushBuffer();

        new ProblemAuthenticationEntryPoint(objectMapper).commence(request, response,
                new InsufficientAuthenticationException("not authenticated"));
        new ProblemAccessDeniedHandler(objectMapper).handle(request, response,
                new AccessDeniedException("not allowed"));

        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(202);
        org.assertj.core.api.Assertions.assertThat(response.getContentAsString()).isEqualTo("already sent");
        org.assertj.core.api.Assertions.assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    static Stream<Object> invalidMerchantClaims() {
        return Stream.of("", " ", " merchant-a-client", "merchant-a-client ", "x".repeat(101), 42);
    }

    private MockHttpServletRequestBuilder validRequest() {
        return post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .content("""
                        {
                          "amount": 10000,
                          "currency": "BRL",
                          "merchantReference": "ORDER-JWT-123",
                          "paymentMethodToken": "tok_approved"
                        }
                        """);
    }

    private MockHttpServletRequestBuilder withBearer(
            MockHttpServletRequestBuilder request,
            String token) {
        return request.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + token);
    }

    private Jwt validJwt() {
        return Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "RS256")
                .subject("internal-service-account-id")
                .issuer("http://localhost:8180/realms/payment-sandbox")
                .audience(List.of("payment-sandbox-api"))
                .claim("azp", MERCHANT_ID)
                .claim(
                        "scope",
                        "payments:create payments:read refunds:create")
                .issuedAt(NOW.minusSeconds(30))
                .expiresAt(NOW.plusSeconds(270))
                .build();
    }

    private Payment payment() {
        return Payment.restore(
                PAYMENT_ID,
                MERCHANT_ID,
                "ORDER-JWT-123",
                new Money(10_000, BRL),
                PaymentStatus.APPROVED,
                NOW,
                NOW);
    }
}
