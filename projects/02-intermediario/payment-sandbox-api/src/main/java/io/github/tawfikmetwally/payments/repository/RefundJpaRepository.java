package io.github.tawfikmetwally.payments.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.tawfikmetwally.payments.entity.RefundEntity;

public interface RefundJpaRepository extends JpaRepository<RefundEntity, UUID> {

    Optional<RefundEntity> findByPayment_IdAndPayment_MerchantId(
            UUID paymentId,
            String merchantId);
}
