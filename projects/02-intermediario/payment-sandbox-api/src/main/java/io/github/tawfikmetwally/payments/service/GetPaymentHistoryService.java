package io.github.tawfikmetwally.payments.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tawfikmetwally.payments.entity.PaymentEventEntity;
import io.github.tawfikmetwally.payments.exception.PaymentNotFoundException;
import io.github.tawfikmetwally.payments.repository.PaymentEventJpaRepository;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;

@Service
public class GetPaymentHistoryService {

    private final PaymentJpaRepository paymentRepository;
    private final PaymentEventJpaRepository paymentEventRepository;

    public GetPaymentHistoryService(
            PaymentJpaRepository paymentRepository,
            PaymentEventJpaRepository paymentEventRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryEntry> getHistory(
            UUID paymentId,
            String merchantId) {
        paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(PaymentNotFoundException::new);

        return paymentEventRepository
                .findByPayment_IdAndPayment_MerchantIdOrderByOccurredAtAsc(
                        paymentId,
                        merchantId)
                .stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    private PaymentHistoryEntry toHistoryEntry(PaymentEventEntity event) {
        return new PaymentHistoryEntry(
                event.getId(),
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getOccurredAt());
    }
}
