package io.github.tawfikmetwally.payments.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tawfikmetwally.payments.domain.Payment;
import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.repository.PaymentJpaRepository;

@Service
public class ListPaymentsService {

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"));

    private final PaymentJpaRepository paymentRepository;

    public ListPaymentsService(PaymentJpaRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public ListPaymentsResult list(ListPaymentsQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), DEFAULT_SORT);
        Page<PaymentEntity> paymentPage = findPage(query, pageable);
        List<Payment> payments = paymentPage.getContent().stream()
                .map(PaymentEntity::toDomain)
                .toList();

        return new ListPaymentsResult(
                payments,
                paymentPage.getNumber(),
                paymentPage.getSize(),
                paymentPage.getTotalElements(),
                paymentPage.getTotalPages());
    }

    private Page<PaymentEntity> findPage(
            ListPaymentsQuery query,
            Pageable pageable) {
        if (query.status() == null) {
            return paymentRepository.findAllByMerchantId(
                    query.merchantId(),
                    pageable);
        }

        return paymentRepository.findAllByMerchantIdAndStatus(
                query.merchantId(),
                query.status(),
                pageable);
    }
}
