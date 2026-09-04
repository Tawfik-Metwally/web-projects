package io.github.tawfikmetwally.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.entity.PaymentEventEntity;
import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;
import io.github.tawfikmetwally.payments.simulator.DeterministicPaymentSimulator;

class CreatePaymentServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-03T18:00:00Z");
    private static final Currency BRL = Currency.getInstance("BRL");

    private PaymentJpaRepository paymentRepository;
    private PaymentEventJpaRepository paymentEventRepository;
    private CreatePaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentJpaRepository.class);
        paymentEventRepository = mock(PaymentEventJpaRepository.class);
        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service = new CreatePaymentService(
                paymentRepository,
                paymentEventRepository,
                new DeterministicPaymentSimulator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsApprovedPaymentAndItsHistory() {
        CreatePaymentResult result = service.create(command("tok_approved"));

        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(result.payment().getMoney().amountMinor()).isEqualTo(10_000);
        assertThat(result.replayed()).isFalse();
        assertPersistedHistory(
                PaymentEventType.PAYMENT_APPROVED,
                PaymentStatus.APPROVED);
    }

    @Test
    void createsDeclinedPaymentAndItsHistory() {
        CreatePaymentResult result = service.create(command("tok_declined"));

        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(result.replayed()).isFalse();
        assertPersistedHistory(
                PaymentEventType.PAYMENT_DECLINED,
                PaymentStatus.DECLINED);
    }

    private CreatePaymentCommand command(String token) {
        return new CreatePaymentCommand(
                "merchant-a",
                "idem-123",
                10_000,
                BRL,
                "ORDER-123",
                token);
    }

    private void assertPersistedHistory(
            PaymentEventType decisionEventType,
            PaymentStatus finalStatus) {
        ArgumentCaptor<PaymentEntity> paymentCaptor =
                ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(finalStatus);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PaymentEventEntity>> eventsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(paymentEventRepository).saveAll(eventsCaptor.capture());

        assertThat(eventsCaptor.getValue())
                .extracting(PaymentEventEntity::getEventType)
                .containsExactly(
                        PaymentEventType.PAYMENT_CREATED,
                        decisionEventType);
        assertThat(eventsCaptor.getValue())
                .extracting(PaymentEventEntity::getFromStatus)
                .containsExactly(null, PaymentStatus.PENDING);
        assertThat(eventsCaptor.getValue())
                .extracting(PaymentEventEntity::getToStatus)
                .containsExactly(PaymentStatus.PENDING, finalStatus);
    }
}
