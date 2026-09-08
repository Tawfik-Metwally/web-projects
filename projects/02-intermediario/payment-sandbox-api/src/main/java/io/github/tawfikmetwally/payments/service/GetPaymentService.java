package io.github.tawfikmetwally.payments.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;

@Service
public class GetPaymentService {

    private final PaymentJpaRepository paymentRepository;

    public GetPaymentService(PaymentJpaRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public Payment getById(UUID paymentId, String merchantId) {
        return paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .map(PaymentEntity::toDomain)
                .orElseThrow(PaymentNotFoundException::new);
    }
}
