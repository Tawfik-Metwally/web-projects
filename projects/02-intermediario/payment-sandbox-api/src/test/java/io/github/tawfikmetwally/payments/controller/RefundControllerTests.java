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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.tawfikmetwally.payments.domain.Money;
import io.github.tawfikmetwally.payments.domain.Refund;
import io.github.tawfikmetwally.payments.enums.RefundStatus;
import io.github.tawfikmetwally.payments.exception.IdempotencyConflictException;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.exception.PaymentNotRefundableException;
import io.github.tawfikmetwally.payments.service.CreateRefundCommand;
import io.github.tawfikmetwally.payments.service.CreateRefundResult;
import io.github.tawfikmetwally.payments.service.CreateRefundService;

@WebMvcTest(RefundController.class)
class RefundControllerTests {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000");
    private static final UUID REFUND_ID = UUID.fromString(
            "11da82b7-d677-4ad3-889e-3b54627b5902");
    private static final String ENDPOINT =
            "/api/v1/payments/" + PAYMENT_ID + "/refunds";
    private static final String MERCHANT_ID = "merchant-a";
    private static final String IDEMPOTENCY_KEY = "refund-key-001";
    private static final Instant NOW = Instant.parse("2026-09-09T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateRefundService createRefundService;

    @Test
    void createsFullRefundUsingPathReasonKeyAndAuthenticatedMerchant()
            throws Exception {
        CreateRefundCommand expectedCommand = command("CUSTOMER_REQUEST");
        when(createRefundService.create(expectedCommand))
                .thenReturn(new CreateRefundResult(refund(), false));

        mockMvc.perform(authenticatedRequest("CUSTOMER_REQUEST"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(REFUND_ID.toString()))
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.amount").value(10_000))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reason").value("CUSTOMER_REQUEST"))
                .andExpect(jsonPath("$.createdAt").value(NOW.toString()))
                .andExpect(jsonPath("$.merchantId").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());

        verify(createRefundService).create(expectedCommand);
        verifyNoMoreInteractions(createRefundService);
    }

    @Test
    void returnsOkAndReplayHeaderWhenServiceReportsAReplay() throws Exception {
        CreateRefundCommand expectedCommand = command("CUSTOMER_REQUEST");
        when(createRefundService.create(expectedCommand))
                .thenReturn(new CreateRefundResult(refund(), true));

        mockMvc.perform(authenticatedRequest("CUSTOMER_REQUEST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(REFUND_ID.toString()));

        verify(createRefundService).create(expectedCommand);
        verifyNoMoreInteractions(createRefundService);
    }

    @Test
    void rejectsBlankReasonWithoutCallingService() throws Exception {
        mockMvc.perform(authenticatedRequest(" "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createRefundService);
    }

    @Test
    void rejectsMissingIdempotencyKeyWithoutCallingService() throws Exception {
        mockMvc.perform(baseRequest("CUSTOMER_REQUEST")
                        .with(user(MERCHANT_ID)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createRefundService);
    }

    @Test
    void returnsConflictForChangedDataWithSameIdempotencyKey()
            throws Exception {
        CreateRefundCommand expectedCommand = command("DUPLICATE_CHARGE");
        when(createRefundService.create(expectedCommand))
                .thenThrow(new IdempotencyConflictException());

        mockMvc.perform(authenticatedRequest("DUPLICATE_CHARGE"))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Idempotency-Replayed"));

        verify(createRefundService).create(expectedCommand);
        verifyNoMoreInteractions(createRefundService);
    }

    @Test
    void returnsNotFoundForMissingOrOtherMerchantPayment() throws Exception {
        CreateRefundCommand expectedCommand = command("CUSTOMER_REQUEST");
        when(createRefundService.create(expectedCommand))
                .thenThrow(new PaymentNotFoundException());

        mockMvc.perform(authenticatedRequest("CUSTOMER_REQUEST"))
                .andExpect(status().isNotFound());

        verify(createRefundService).create(expectedCommand);
        verifyNoMoreInteractions(createRefundService);
    }

    @Test
    void returnsConflictForOwnNonRefundablePayment() throws Exception {
        CreateRefundCommand expectedCommand = command("CUSTOMER_REQUEST");
        when(createRefundService.create(expectedCommand))
                .thenThrow(new PaymentNotRefundableException());

        mockMvc.perform(authenticatedRequest("CUSTOMER_REQUEST"))
                .andExpect(status().isConflict());

        verify(createRefundService).create(expectedCommand);
        verifyNoMoreInteractions(createRefundService);
    }

    @Test
    void rejectsMalformedPaymentIdWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/payments/not-a-uuid/refunds")
                        .with(user(MERCHANT_ID))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content("{\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createRefundService);
    }

    @Test
    void rejectsUnauthenticatedRequestBeforeCallingService() throws Exception {
        mockMvc.perform(baseRequest("CUSTOMER_REQUEST")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(createRefundService);
    }

    private MockHttpServletRequestBuilder authenticatedRequest(String reason) {
        return baseRequest(reason)
                .with(user(MERCHANT_ID))
                .header("Idempotency-Key", IDEMPOTENCY_KEY);
    }

    private MockHttpServletRequestBuilder baseRequest(String reason) {
        return post(ENDPOINT)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}");
    }

    private CreateRefundCommand command(String reason) {
        return new CreateRefundCommand(
                MERCHANT_ID,
                IDEMPOTENCY_KEY,
                PAYMENT_ID,
                reason);
    }

    private Refund refund() {
        return Refund.restore(
                REFUND_ID,
                PAYMENT_ID,
                new Money(10_000, Currency.getInstance("BRL")),
                RefundStatus.COMPLETED,
                "CUSTOMER_REQUEST",
                NOW);
    }
}
