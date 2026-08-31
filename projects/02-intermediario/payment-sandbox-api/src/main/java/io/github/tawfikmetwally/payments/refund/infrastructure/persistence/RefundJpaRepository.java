package io.github.tawfikmetwally.payments.refund.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundJpaRepository extends JpaRepository<RefundEntity, UUID> {

    Optional<RefundEntity> findByPayment_IdAndPayment_MerchantId(
            UUID paymentId,
            String merchantId);
}
