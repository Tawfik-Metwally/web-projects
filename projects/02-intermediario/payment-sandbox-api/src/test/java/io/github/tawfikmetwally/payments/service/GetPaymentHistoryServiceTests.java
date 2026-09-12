package io.github.tawfikmetwally.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.entity.PaymentEventEntity;
import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;

class GetPaymentHistoryServiceTests {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000");
    private static final String MERCHANT_ID = "merchant-a";
    private static final Instant CREATED_AT = Instant.parse(
            "2026-09-12T14:00:00Z");
    private static final Instant REFUNDED_AT = Instant.parse(
            "2026-09-12T15:00:00Z");

    private PaymentJpaRepository paymentRepository;
    private PaymentEventJpaRepository paymentEventRepository;
    private GetPaymentHistoryService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentJpaRepository.class);
        paymentEventRepository = mock(PaymentEventJpaRepository.class);
        service = new GetPaymentHistoryService(
                paymentRepository,
                paymentEventRepository);
    }

    @Test
    void returnsHistoryForOwnPaymentInRepositoryOrder() {
        PaymentEntity payment = paymentEntity();
        List<PaymentEventEntity> events = List.of(
                event(
                        payment,
                        PaymentEventType.PAYMENT_CREATED,
                        null,
                        PaymentStatus.PENDING,
                        CREATED_AT),
                event(
                        payment,
                        PaymentEventType.PAYMENT_APPROVED,
                        PaymentStatus.PENDING,
                        PaymentStatus.APPROVED,
                        CREATED_AT),
                event(
                        payment,
                        PaymentEventType.PAYMENT_REFUNDED,
                        PaymentStatus.APPROVED,
                        PaymentStatus.REFUNDED,
                        REFUNDED_AT));
        when(paymentRepository.findByIdAndMerchantId(PAYMENT_ID, MERCHANT_ID))
                .thenReturn(Optional.of(payment));
        when(paymentEventRepository
                .findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
                        PAYMENT_ID,
                        MERCHANT_ID))
                .thenReturn(events);

        List<PaymentHistoryEntry> result = service.getHistory(
                PAYMENT_ID,
                MERCHANT_ID);

        assertThat(result)
                .extracting(PaymentHistoryEntry::eventType)
                .containsExactly(
                        PaymentEventType.PAYMENT_CREATED,
                        PaymentEventType.PAYMENT_APPROVED,
                        PaymentEventType.PAYMENT_REFUNDED);
        assertThat(result.get(0).fromStatus()).isNull();
        assertThat(result.get(2).fromStatus())
                .isEqualTo(PaymentStatus.APPROVED);
        assertThat(result.get(2).toStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.get(2).occurredAt()).isEqualTo(REFUNDED_AT);
        verify(paymentRepository).findByIdAndMerchantId(PAYMENT_ID, MERCHANT_ID);
        verify(paymentEventRepository)
                .findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
                        PAYMENT_ID,
                        MERCHANT_ID);
    }

    @Test
    void returnsEmptyHistoryForOwnPaymentWithoutEvents() {
        when(paymentRepository.findByIdAndMerchantId(PAYMENT_ID, MERCHANT_ID))
                .thenReturn(Optional.of(paymentEntity()));
        when(paymentEventRepository
                .findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
                        PAYMENT_ID,
                        MERCHANT_ID))
                .thenReturn(List.of());

        assertThat(service.getHistory(PAYMENT_ID, MERCHANT_ID)).isEmpty();

        verify(paymentRepository).findByIdAndMerchantId(PAYMENT_ID, MERCHANT_ID);
        verify(paymentEventRepository)
                .findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
                        PAYMENT_ID,
                        MERCHANT_ID);
    }

    @Test
    void hidesMissingOrOtherMerchantPaymentAsNotFound() {
        when(paymentRepository.findByIdAndMerchantId(PAYMENT_ID, MERCHANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory(PAYMENT_ID, MERCHANT_ID))
                .isInstanceOf(PaymentNotFoundException.class);

        verify(paymentRepository).findByIdAndMerchantId(PAYMENT_ID, MERCHANT_ID);
        verifyNoMoreInteractions(paymentRepository);
        verifyNoInteractions(paymentEventRepository);
    }

    private PaymentEntity paymentEntity() {
        return new PaymentEntity(
                PAYMENT_ID,
                MERCHANT_ID,
                "ORDER-123",
                10_000,
                "BRL",
                PaymentStatus.REFUNDED,
                CREATED_AT,
                REFUNDED_AT);
    }

    private PaymentEventEntity event(
            PaymentEntity payment,
            PaymentEventType eventType,
            PaymentStatus fromStatus,
            PaymentStatus toStatus,
            Instant occurredAt) {
        return new PaymentEventEntity(
                UUID.randomUUID(),
                payment,
                eventType,
                fromStatus,
                toStatus,
                occurredAt);
    }
}
