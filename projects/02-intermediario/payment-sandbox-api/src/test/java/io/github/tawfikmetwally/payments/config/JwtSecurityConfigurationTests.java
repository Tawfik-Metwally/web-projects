package io.github.tawfikmetwally.payments.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.tawfikmetwally.payments.controller.PaymentController;
import io.github.tawfikmetwally.payments.domain.Money;
import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
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
    private static final UUID PAYMENT_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant NOW = Instant.parse("2026-09-14T18:00:00Z");
    private static final Currency BRL = Currency.getInstance("BRL");

    @Autowired
    private MockMvc mockMvc;

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
        mockMvc.perform(validRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void ignoresRemovedDemoMerchantHeader() throws Exception {
        mockMvc.perform(validRequest()
                        .header("X-Demo-Merchant-Id", MERCHANT_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void rejectsTokenThatJwtDecoderCannotValidate() throws Exception {
        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new BadJwtException("invalid token"));

        mockMvc.perform(withBearer(validRequest(), "invalid-token"))
                .andExpect(status().isUnauthorized())
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
                .andExpect(status().isUnauthorized());
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
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(createPaymentService, getPaymentService, listPaymentsService);
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
