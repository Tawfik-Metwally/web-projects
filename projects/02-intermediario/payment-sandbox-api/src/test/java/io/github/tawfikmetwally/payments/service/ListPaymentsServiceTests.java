package io.github.tawfikmetwally.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;

class ListPaymentsServiceTests {

    private static final String MERCHANT_ID = "merchant-a";
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"));

    private PaymentJpaRepository paymentRepository;
    private ListPaymentsService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentJpaRepository.class);
        service = new ListPaymentsService(paymentRepository);
    }

    @Test
    void listsMerchantPaymentsWithoutStatusFilter() {
        ListPaymentsQuery query = new ListPaymentsQuery(MERCHANT_ID, 1, 2, null);
        Pageable pageable = PageRequest.of(1, 2, DEFAULT_SORT);
        PaymentEntity paymentEntity = mock(PaymentEntity.class);
        Payment payment = mock(Payment.class);
        when(paymentRepository.findAllByMerchantId(MERCHANT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(paymentEntity), pageable, 3));
        when(paymentEntity.toDomain()).thenReturn(payment);

        ListPaymentsResult result = service.list(query);

        assertThat(result.payments()).containsExactly(payment);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
        verify(paymentRepository).findAllByMerchantId(MERCHANT_ID, pageable);
        verify(paymentEntity).toDomain();
        verifyNoMoreInteractions(paymentRepository, paymentEntity);
    }

    @Test
    void listsMerchantPaymentsUsingStatusFilter() {
        ListPaymentsQuery query = new ListPaymentsQuery(
                MERCHANT_ID,
                0,
                20,
                PaymentStatus.APPROVED);
        Pageable pageable = PageRequest.of(0, 20, DEFAULT_SORT);
        when(paymentRepository.findAllByMerchantIdAndStatus(
                MERCHANT_ID,
                PaymentStatus.APPROVED,
                pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        ListPaymentsResult result = service.list(query);

        assertThat(result.payments()).isEmpty();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        verify(paymentRepository).findAllByMerchantIdAndStatus(
                MERCHANT_ID,
                PaymentStatus.APPROVED,
                pageable);
        verifyNoMoreInteractions(paymentRepository);
    }
}
