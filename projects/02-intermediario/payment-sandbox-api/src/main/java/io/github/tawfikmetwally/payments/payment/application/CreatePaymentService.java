package io.github.tawfikmetwally.payments.payment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tawfikmetwally.payments.payment.domain.DeterministicPaymentSimulator;
import io.github.tawfikmetwally.payments.payment.domain.Money;
import io.github.tawfikmetwally.payments.payment.domain.Payment;
import io.github.tawfikmetwally.payments.payment.domain.PaymentDecision;
import io.github.tawfikmetwally.payments.payment.domain.PaymentEventType;
import io.github.tawfikmetwally.payments.payment.domain.PaymentStatus;
import io.github.tawfikmetwally.payments.payment.infrastructure.persistence.PaymentEntity;
import io.github.tawfikmetwally.payments.payment.infrastructure.persistence.PaymentEventEntity;
import io.github.tawfikmetwally.payments.payment.infrastructure.persistence.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.payment.infrastructure.persistence.PaymentJpaRepository;

@Service
public class CreatePaymentService {

    private final PaymentJpaRepository paymentRepository;
    private final PaymentEventJpaRepository paymentEventRepository;
    private final DeterministicPaymentSimulator paymentSimulator;
    private final Clock clock;

    public CreatePaymentService(
            PaymentJpaRepository paymentRepository,
            PaymentEventJpaRepository paymentEventRepository,
            DeterministicPaymentSimulator paymentSimulator,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.paymentSimulator = paymentSimulator;
        this.clock = clock;
    }

    @Transactional
    public CreatePaymentResult create(CreatePaymentCommand command) {
        Instant occurredAt = clock.instant();
        Payment payment = Payment.create(
                UUID.randomUUID(),
                command.merchantId(),
                command.merchantReference(),
                new Money(command.amountMinor(), command.currency()),
                occurredAt);

        PaymentDecision decision = paymentSimulator.decide(command.paymentMethodToken());
        applyDecision(payment, decision, occurredAt);

        PaymentEntity paymentEntity = paymentRepository.save(PaymentEntity.fromDomain(payment));
        paymentEventRepository.saveAll(List.of(
                createdEvent(paymentEntity, occurredAt),
                decisionEvent(paymentEntity, decision, occurredAt)));

        return new CreatePaymentResult(payment, false);
    }

    private void applyDecision(Payment payment, PaymentDecision decision, Instant occurredAt) {
        switch (decision) {
            case APPROVE -> payment.approve(occurredAt);
            case DECLINE -> payment.decline(occurredAt);
        }
    }

    private PaymentEventEntity createdEvent(PaymentEntity payment, Instant occurredAt) {
        return new PaymentEventEntity(
                UUID.randomUUID(),
                payment,
                PaymentEventType.PAYMENT_CREATED,
                null,
                PaymentStatus.PENDING,
                occurredAt);
    }

    private PaymentEventEntity decisionEvent(
            PaymentEntity payment,
            PaymentDecision decision,
            Instant occurredAt) {
        PaymentEventType eventType = switch (decision) {
            case APPROVE -> PaymentEventType.PAYMENT_APPROVED;
            case DECLINE -> PaymentEventType.PAYMENT_DECLINED;
        };

        return new PaymentEventEntity(
                UUID.randomUUID(),
                payment,
                eventType,
                PaymentStatus.PENDING,
                payment.getStatus(),
                occurredAt);
    }
}
