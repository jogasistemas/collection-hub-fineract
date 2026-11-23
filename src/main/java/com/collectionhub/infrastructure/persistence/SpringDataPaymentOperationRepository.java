package com.collectionhub.infrastructure.persistence;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SpringDataPaymentOperationRepository extends JpaRepository<PaymentOperationEntity, Long> {
    Optional<PaymentOperationEntity> findByIdempotencyKey(String key);
}
