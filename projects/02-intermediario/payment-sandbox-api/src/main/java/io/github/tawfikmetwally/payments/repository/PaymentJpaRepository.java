package io.github.tawfikmetwally.payments.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tawfikmetwally.payments.entity.PaymentEntity;
import io.github.tawfikmetwally.payments.enums.PaymentStatus;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByIdAndMerchantId(UUID id, String merchantId);

    Page<PaymentEntity> findAllByMerchantId(String merchantId, Pageable pageable);

    Page<PaymentEntity> findAllByMerchantIdAndStatus(
            String merchantId,
            PaymentStatus status,
            Pageable pageable);
}
