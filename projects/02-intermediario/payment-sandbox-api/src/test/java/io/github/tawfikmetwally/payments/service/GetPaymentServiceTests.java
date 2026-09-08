package io.github.tawfikmetwally.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;

class GetPaymentServiceTests {

    private static final UUID PAYMENT_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String MERCHANT_ID = "merchant-a";

    private PaymentJpaRepository paymentRepository;
    private GetPaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentJpaRepository.class);
        service = new GetPaymentService(paymentRepository);
    }

    @Test
    void returnsPaymentFoundForMerchant() {
        Payment expectedPayment = mock(Payment.class);
        PaymentEntity paymentEntity = mock(PaymentEntity.class);
        when(paymentRepository.findByIdAndMerchantId(PAYMENT_ID, MERCHANT_ID))
                .thenReturn(Optional.of(paymentEntity));
        when(paymentEntity.toDomain()).thenReturn(expectedPayment);

        Payment result = service.getById(PAYMENT_ID, MERCHANT_ID);

        assertThat(result).isSameAs(expectedPayment);
        verify(paymentRepository).findByIdAndMerchantId(PAYMENT_ID, MERCHANT_ID);
        verify(paymentEntity).toDomain();
        verifyNoMoreInteractions(paymentRepository, paymentEntity);
    }

    @Test
    void throwsNotFoundWhenPaymentIsUnavailableToMerchant() {
        when(paymentRepository.findByIdAndMerchantId(PAYMENT_ID, MERCHANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(PAYMENT_ID, MERCHANT_ID))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment was not found");

        verify(paymentRepository).findByIdAndMerchantId(PAYMENT_ID, MERCHANT_ID);
        verifyNoMoreInteractions(paymentRepository);
    }
}
