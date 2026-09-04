package io.github.tawfikmetwally.payments.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.tawfikmetwally.payments.domain.Money;
import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.exception.UnsupportedPaymentMethodTokenException;
import io.github.tawfikmetwally.payments.service.CreatePaymentCommand;
import io.github.tawfikmetwally.payments.service.CreatePaymentResult;
import io.github.tawfikmetwally.payments.service.CreatePaymentService;

@WebMvcTest(PaymentController.class)
class PaymentControllerTests {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String MERCHANT_ID = "merchant-a";
    private static final String IDEMPOTENCY_KEY = "idem-123";
    private static final UUID PAYMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant NOW = Instant.parse("2026-09-03T18:00:00Z");
    private static final Currency BRL = Currency.getInstance("BRL");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePaymentService createPaymentService;

    @ParameterizedTest
    @CsvSource({ "tok_approved, APPROVED", "tok_declined, DECLINED" })
    void createsPaymentUsingBodyHeaderAndAuthenticatedMerchant(
            String token,
            PaymentStatus paymentStatus) throws Exception {
        CreatePaymentCommand expectedCommand = command(token);
        when(createPaymentService.create(expectedCommand))
                .thenReturn(new CreatePaymentResult(payment(paymentStatus), false));

        mockMvc.perform(authenticatedRequest()
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Merchant-Id", "merchant-b")
                        .content(body(10_000L, "BRL", token)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", ENDPOINT + "/" + PAYMENT_ID))
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.amount").value(10_000))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.status").value(paymentStatus.name()))
                .andExpect(jsonPath("$.merchantReference").value("ORDER-123"))
                .andExpect(jsonPath("$.createdAt").value(NOW.toString()))
                .andExpect(jsonPath("$.updatedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.merchantId").doesNotExist())
                .andExpect(jsonPath("$.paymentMethodToken").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());

        verify(createPaymentService).create(expectedCommand);
        verifyNoMoreInteractions(createPaymentService);
    }

    @Test
    void returnsOkAndReplayHeaderWhenServiceReportsAReplay() throws Exception {
        CreatePaymentCommand expectedCommand = command("tok_approved");
        when(createPaymentService.create(expectedCommand))
                .thenReturn(new CreatePaymentResult(payment(PaymentStatus.APPROVED), true));

        mockMvc.perform(authenticatedRequest()
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(body(10_000L, "BRL", "tok_approved")))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(PAYMENT_ID.toString()));

        verify(createPaymentService).create(expectedCommand);
        verifyNoMoreInteractions(createPaymentService);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, -1 })
    void rejectsNonPositiveAmountWithoutCallingService(long amount) throws Exception {
        mockMvc.perform(authenticatedRequest()
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(body(amount, "BRL", "tok_approved")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void rejectsNullAmountWithoutCallingService() throws Exception {
        mockMvc.perform(authenticatedRequest()
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(body(null, "BRL", "tok_approved")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void rejectsMissingIdempotencyKeyWithoutCallingService() throws Exception {
        mockMvc.perform(authenticatedRequest()
                        .content(body(10_000L, "BRL", "tok_approved")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void rejectsBlankIdempotencyKeyWithoutCallingService() throws Exception {
        mockMvc.perform(authenticatedRequest()
                        .header("Idempotency-Key", " ")
                        .content(body(10_000L, "BRL", "tok_approved")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void rejectsIdempotencyKeyLongerThanDatabaseLimitWithoutCallingService() throws Exception {
        mockMvc.perform(authenticatedRequest()
                        .header("Idempotency-Key", "k".repeat(256))
                        .content(body(10_000L, "BRL", "tok_approved")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createPaymentService);
    }

    @ParameterizedTest
    @ValueSource(strings = { "brl", "ZZZ", "USD" })
    void rejectsInvalidOrUnsupportedCurrencyWithoutCallingService(String currency) throws Exception {
        mockMvc.perform(authenticatedRequest()
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(body(10_000L, currency, "tok_approved")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createPaymentService);
    }

    @Test
    void returnsBadRequestWhenServiceRejectsUnknownPaymentMethodToken() throws Exception {
        CreatePaymentCommand expectedCommand = command("tok_unknown");
        when(createPaymentService.create(expectedCommand))
                .thenThrow(new UnsupportedPaymentMethodTokenException());

        mockMvc.perform(authenticatedRequest()
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(body(10_000L, "BRL", "tok_unknown")))
                .andExpect(status().isBadRequest());

        verify(createPaymentService).create(expectedCommand);
        verifyNoMoreInteractions(createPaymentService);
    }

    @Test
    void rejectsUnauthenticatedRequestWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(body(10_000L, "BRL", "tok_approved")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(createPaymentService);
    }

    private MockHttpServletRequestBuilder authenticatedRequest() {
        return post(ENDPOINT)
                .with(user(MERCHANT_ID))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
    }

    private String body(Long amount, String currency, String token) {
        return """
                {
                  "amount": %s,
                  "currency": "%s",
                  "merchantReference": "ORDER-123",
                  "paymentMethodToken": "%s"
                }
                """.formatted(amount, currency, token);
    }

    private CreatePaymentCommand command(String token) {
        return new CreatePaymentCommand(
                MERCHANT_ID,
                IDEMPOTENCY_KEY,
                10_000,
                BRL,
                "ORDER-123",
                token);
    }

    private Payment payment(PaymentStatus status) {
        return Payment.restore(
                PAYMENT_ID,
                MERCHANT_ID,
                "ORDER-123",
                new Money(10_000, BRL),
                status,
                NOW,
                NOW);
    }
}
