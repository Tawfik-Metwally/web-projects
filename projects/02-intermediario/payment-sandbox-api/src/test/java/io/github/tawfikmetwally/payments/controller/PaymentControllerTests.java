package io.github.tawfikmetwally.payments.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
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
import io.github.tawfikmetwally.payments.exception.IdempotencyConflictException;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.exception.UnsupportedPaymentMethodTokenException;
import io.github.tawfikmetwally.payments.service.CreatePaymentCommand;
import io.github.tawfikmetwally.payments.service.CreatePaymentResult;
import io.github.tawfikmetwally.payments.service.CreatePaymentService;
import io.github.tawfikmetwally.payments.service.GetPaymentService;
import io.github.tawfikmetwally.payments.service.ListPaymentsQuery;
import io.github.tawfikmetwally.payments.service.ListPaymentsResult;
import io.github.tawfikmetwally.payments.service.ListPaymentsService;

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

    @MockitoBean
    private GetPaymentService getPaymentService;

    @MockitoBean
    private ListPaymentsService listPaymentsService;

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
    void returnsConflictWhenServiceRejectsReusedKeyWithDifferentData() throws Exception {
        CreatePaymentCommand expectedCommand = command("tok_declined");
        when(createPaymentService.create(expectedCommand))
                .thenThrow(new IdempotencyConflictException());

        mockMvc.perform(authenticatedRequest()
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(body(10_000L, "BRL", "tok_declined")))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().doesNotExist("Idempotency-Replayed"));

        verify(createPaymentService).create(expectedCommand);
        verifyNoMoreInteractions(createPaymentService);
    }

    @Test
    void returnsPaymentFoundByIdForAuthenticatedMerchant() throws Exception {
        when(getPaymentService.getById(PAYMENT_ID, MERCHANT_ID))
                .thenReturn(payment(PaymentStatus.APPROVED));

        mockMvc.perform(get(ENDPOINT + "/" + PAYMENT_ID)
                        .with(user(MERCHANT_ID))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.amount").value(10_000))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.merchantReference").value("ORDER-123"))
                .andExpect(jsonPath("$.merchantId").doesNotExist());

        verify(getPaymentService).getById(PAYMENT_ID, MERCHANT_ID);
        verifyNoMoreInteractions(getPaymentService);
    }

    @Test
    void returnsNotFoundWhenPaymentIsUnavailableToAuthenticatedMerchant()
            throws Exception {
        when(getPaymentService.getById(PAYMENT_ID, MERCHANT_ID))
                .thenThrow(new PaymentNotFoundException());

        mockMvc.perform(get(ENDPOINT + "/" + PAYMENT_ID)
                        .with(user(MERCHANT_ID))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(getPaymentService).getById(PAYMENT_ID, MERCHANT_ID);
        verifyNoMoreInteractions(getPaymentService);
    }

    @Test
    void rejectsMalformedPaymentIdWithoutCallingGetService() throws Exception {
        mockMvc.perform(get(ENDPOINT + "/not-a-uuid")
                        .with(user(MERCHANT_ID))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getPaymentService);
    }

    @Test
    void listsPaymentsUsingDefaultPaginationForAuthenticatedMerchant() throws Exception {
        ListPaymentsQuery expectedQuery = new ListPaymentsQuery(
                MERCHANT_ID,
                0,
                20,
                null);
        when(listPaymentsService.list(expectedQuery))
                .thenReturn(new ListPaymentsResult(
                        List.of(payment(PaymentStatus.APPROVED)),
                        0,
                        20,
                        1,
                        1));

        mockMvc.perform(get(ENDPOINT)
                        .with(user(MERCHANT_ID))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.content[0].amount").value(10_000))
                .andExpect(jsonPath("$.content[0].currency").value("BRL"))
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.content[0].merchantId").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(listPaymentsService).list(expectedQuery);
        verifyNoMoreInteractions(listPaymentsService);
    }

    @Test
    void listsPaymentsUsingRequestedPageSizeAndStatus() throws Exception {
        ListPaymentsQuery expectedQuery = new ListPaymentsQuery(
                MERCHANT_ID,
                2,
                5,
                PaymentStatus.DECLINED);
        when(listPaymentsService.list(expectedQuery))
                .thenReturn(new ListPaymentsResult(
                        List.of(payment(PaymentStatus.DECLINED)),
                        2,
                        5,
                        11,
                        3));

        mockMvc.perform(get(ENDPOINT)
                        .with(user(MERCHANT_ID))
                        .queryParam("page", "2")
                        .queryParam("size", "5")
                        .queryParam("status", "DECLINED")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("DECLINED"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(listPaymentsService).list(expectedQuery);
        verifyNoMoreInteractions(listPaymentsService);
    }

    @Test
    void rejectsNegativePageWithoutCallingListService() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .with(user(MERCHANT_ID))
                        .queryParam("page", "-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listPaymentsService);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 101 })
    void rejectsSizeOutsideAllowedRangeWithoutCallingListService(int size) throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .with(user(MERCHANT_ID))
                        .queryParam("size", Integer.toString(size))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listPaymentsService);
    }

    @Test
    void rejectsUnknownStatusWithoutCallingListService() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .with(user(MERCHANT_ID))
                        .queryParam("status", "UNKNOWN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listPaymentsService);
    }

    @Test
    void rejectsUnauthenticatedListRequestWithoutCallingListService() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(listPaymentsService);
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
