package io.github.tawfikmetwally.payments.repository;

import io.github.tawfikmetwally.payments.entity.RefundEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<RefundEntity, UUID> {

    Optional<RefundEntity> findByPayment_IdAndPayment_MerchantId(UUID paymentId, String merchantId);
}
