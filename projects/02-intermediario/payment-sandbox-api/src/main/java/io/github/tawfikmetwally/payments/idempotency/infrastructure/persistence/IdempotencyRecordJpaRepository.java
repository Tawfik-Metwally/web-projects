package io.github.tawfikmetwally.payments.idempotency.infrastructure.persistence;

import io.github.tawfikmetwally.payments.idempotency.domain.IdempotencyOperation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordJpaRepository
        extends JpaRepository<IdempotencyRecordEntity, UUID> {

    Optional<IdempotencyRecordEntity> findByMerchantIdAndOperationTypeAndIdempotencyKey(
            String merchantId,
            IdempotencyOperation operationType,
            String idempotencyKey);
}
