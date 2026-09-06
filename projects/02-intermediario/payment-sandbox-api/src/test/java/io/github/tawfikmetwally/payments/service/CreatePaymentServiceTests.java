package io.github.tawfikmetwally.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import io.github.tawfikmetwally.payments.entity.IdempotencyRecordEntity;
import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.entity.PaymentEventEntity;
import io.github.tawfikmetwally.payments.enums.IdempotencyOperation;
import io.github.tawfikmetwally.payments.enums.PaymentEventType;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.exception.IdempotencyConflictException;
import io.github.tawfikmetwally.payments.repository.IdempotencyRecordJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;
import io.github.tawfikmetwally.payments.simulator.DeterministicPaymentSimulator;

class CreatePaymentServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-03T18:00:00Z");
    private static final Currency BRL = Currency.getInstance("BRL");

    private PaymentJpaRepository paymentRepository;
    private PaymentEventJpaRepository paymentEventRepository;
    private IdempotencyRecordJpaRepository idempotencyRecordRepository;
    private DeterministicPaymentSimulator paymentSimulator;
    private final CreatePaymentRequestHasher requestHasher = new CreatePaymentRequestHasher();
    private CreatePaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentJpaRepository.class);
        paymentEventRepository = mock(PaymentEventJpaRepository.class);
        idempotencyRecordRepository = mock(IdempotencyRecordJpaRepository.class);
        paymentSimulator = spy(new DeterministicPaymentSimulator());
        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service = new CreatePaymentService(
                paymentRepository,
                paymentEventRepository,
                idempotencyRecordRepository,
                requestHasher,
                paymentSimulator,
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
        assertPersistedIdempotency(command("tok_approved"), result);
        verify(paymentSimulator).decide("tok_approved");
    }

    @Test
    void createsDeclinedPaymentAndItsHistory() {
        CreatePaymentResult result = service.create(command("tok_declined"));

        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(result.replayed()).isFalse();
        assertPersistedHistory(
                PaymentEventType.PAYMENT_DECLINED,
                PaymentStatus.DECLINED);
        assertPersistedIdempotency(command("tok_declined"), result);
        verify(paymentSimulator).decide("tok_declined");
    }

    @ParameterizedTest
    @CsvSource({ "tok_approved, APPROVED", "tok_declined, DECLINED" })
    void replaysExistingPaymentWithoutProcessingOrWritingAgain(
            String token, PaymentStatus status) {
        CreatePaymentCommand command = command(token);
        IdempotencyRecordEntity record = existingRecord(command, status);
        when(idempotencyRecordRepository.findByMerchantIdAndOperationTypeAndIdempotencyKey(
                command.merchantId(), IdempotencyOperation.CREATE_PAYMENT, command.idempotencyKey()))
                .thenReturn(Optional.of(record));

        CreatePaymentResult result = service.create(command);

        assertThat(result.replayed()).isTrue();
        assertThat(result.payment()).usingRecursiveComparison()
                .isEqualTo(record.getPayment().toDomain());
        verifyNoInteractions(paymentSimulator, paymentRepository, paymentEventRepository);
        verify(idempotencyRecordRepository).findByMerchantIdAndOperationTypeAndIdempotencyKey(
                command.merchantId(), IdempotencyOperation.CREATE_PAYMENT, command.idempotencyKey());
        verifyNoMoreInteractions(idempotencyRecordRepository);
    }

    @Test
    void rejectsChangedDataWithoutProcessingOrWritingAgain() {
        IdempotencyRecordEntity record = existingRecord(command("tok_approved"), PaymentStatus.APPROVED);
        CreatePaymentCommand changedCommand = command("tok_declined");
        when(idempotencyRecordRepository.findByMerchantIdAndOperationTypeAndIdempotencyKey(
                changedCommand.merchantId(), IdempotencyOperation.CREATE_PAYMENT,
                changedCommand.idempotencyKey()))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.create(changedCommand))
                .isInstanceOf(IdempotencyConflictException.class);

        verifyNoInteractions(paymentSimulator, paymentRepository, paymentEventRepository);
        verify(idempotencyRecordRepository).findByMerchantIdAndOperationTypeAndIdempotencyKey(
                changedCommand.merchantId(), IdempotencyOperation.CREATE_PAYMENT,
                changedCommand.idempotencyKey());
        verifyNoMoreInteractions(idempotencyRecordRepository);
    }

    private IdempotencyRecordEntity existingRecord(CreatePaymentCommand command, PaymentStatus status) {
        Instant createdAt = NOW.minusSeconds(300);
        PaymentEntity payment = new PaymentEntity(
                UUID.randomUUID(), command.merchantId(), command.merchantReference(),
                command.amountMinor(), command.currency().getCurrencyCode(), status,
                createdAt, createdAt);

        return new IdempotencyRecordEntity(
                UUID.randomUUID(), command.merchantId(), IdempotencyOperation.CREATE_PAYMENT,
                command.idempotencyKey(), requestHasher.hash(command), payment, createdAt);
    }

    private void assertPersistedIdempotency(CreatePaymentCommand command, CreatePaymentResult result) {
        ArgumentCaptor<IdempotencyRecordEntity> captor =
                ArgumentCaptor.forClass(IdempotencyRecordEntity.class);
        verify(idempotencyRecordRepository).findByMerchantIdAndOperationTypeAndIdempotencyKey(
                command.merchantId(), IdempotencyOperation.CREATE_PAYMENT, command.idempotencyKey());
        verify(idempotencyRecordRepository).save(captor.capture());
        verifyNoMoreInteractions(idempotencyRecordRepository);

        IdempotencyRecordEntity record = captor.getValue();
        assertThat(record.getId()).isNotNull();
        assertThat(record.getMerchantId()).isEqualTo(command.merchantId());
        assertThat(record.getOperationType()).isEqualTo(IdempotencyOperation.CREATE_PAYMENT);
        assertThat(record.getIdempotencyKey()).isEqualTo(command.idempotencyKey());
        assertThat(record.getRequestHash()).isEqualTo(requestHasher.hash(command));
        assertThat(record.getPayment().getId()).isEqualTo(result.payment().getId());
        assertThat(record.getCreatedAt()).isEqualTo(NOW);
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
