package io.github.tawfikmetwally.payments.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tawfikmetwally.payments.entity.PaymentEntity;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByIdAndMerchantId(UUID id, String merchantId);
}
