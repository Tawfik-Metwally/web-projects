package io.github.tawfikmetwally.payments.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.service.GetPaymentHistoryService;
import io.github.tawfikmetwally.payments.service.PaymentHistoryEntry;

@WebMvcTest(PaymentEventController.class)
class PaymentEventControllerTests {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000");
    private static final String ENDPOINT =
            "/api/v1/payments/" + PAYMENT_ID + "/events";
    private static final String MERCHANT_ID = "merchant-a";
    private static final Instant CREATED_AT = Instant.parse(
            "2026-09-12T14:00:00Z");
    private static final Instant REFUNDED_AT = Instant.parse(
            "2026-09-12T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetPaymentHistoryService getPaymentHistoryService;

    @Test
    void returnsChronologicalHistoryForAuthenticatedMerchant() throws Exception {
        List<PaymentHistoryEntry> history = List.of(
                entry(
                        PaymentEventType.PAYMENT_CREATED,
                        null,
                        PaymentStatus.PENDING,
                        CREATED_AT),
                entry(
                        PaymentEventType.PAYMENT_APPROVED,
                        PaymentStatus.PENDING,
                        PaymentStatus.APPROVED,
                        CREATED_AT),
                entry(
                        PaymentEventType.PAYMENT_REFUNDED,
                        PaymentStatus.APPROVED,
                        PaymentStatus.REFUNDED,
                        REFUNDED_AT));
        when(getPaymentHistoryService.getHistory(PAYMENT_ID, MERCHANT_ID))
                .thenReturn(history);

        mockMvc.perform(get(ENDPOINT)
                        .with(user(MERCHANT_ID))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].eventType")
                        .value("PAYMENT_CREATED"))
                .andExpect(jsonPath("$[0].fromStatus").value(nullValue()))
                .andExpect(jsonPath("$[0].toStatus").value("PENDING"))
                .andExpect(jsonPath("$[1].eventType")
                        .value("PAYMENT_APPROVED"))
                .andExpect(jsonPath("$[2].eventType")
                        .value("PAYMENT_REFUNDED"))
                .andExpect(jsonPath("$[2].fromStatus").value("APPROVED"))
                .andExpect(jsonPath("$[2].toStatus").value("REFUNDED"))
                .andExpect(jsonPath("$[2].occurredAt")
                        .value(REFUNDED_AT.toString()));

        verify(getPaymentHistoryService).getHistory(PAYMENT_ID, MERCHANT_ID);
        verifyNoMoreInteractions(getPaymentHistoryService);
    }

    @Test
    void returnsNotFoundForMissingOrOtherMerchantPayment() throws Exception {
        when(getPaymentHistoryService.getHistory(PAYMENT_ID, MERCHANT_ID))
                .thenThrow(new PaymentNotFoundException());

        mockMvc.perform(get(ENDPOINT).with(user(MERCHANT_ID)))
                .andExpect(status().isNotFound());

        verify(getPaymentHistoryService).getHistory(PAYMENT_ID, MERCHANT_ID);
        verifyNoMoreInteractions(getPaymentHistoryService);
    }

    @Test
    void rejectsMalformedPaymentIdWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/v1/payments/not-a-uuid/events")
                        .with(user(MERCHANT_ID)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getPaymentHistoryService);
    }

    @Test
    void rejectsUnauthenticatedRequestBeforeCallingService() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getPaymentHistoryService);
    }

    private PaymentHistoryEntry entry(
            PaymentEventType eventType,
            PaymentStatus fromStatus,
            PaymentStatus toStatus,
            Instant occurredAt) {
        return new PaymentHistoryEntry(
                UUID.randomUUID(),
                eventType,
                fromStatus,
                toStatus,
                occurredAt);
    }
}
