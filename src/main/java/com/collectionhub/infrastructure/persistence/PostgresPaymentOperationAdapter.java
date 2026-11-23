package com.collectionhub.infrastructure.persistence;

import com.collectionhub.application.PaymentConflictException;
import com.collectionhub.domain.model.PaymentOperation;
import com.collectionhub.domain.model.PaymentStatus;
import com.collectionhub.domain.port.out.PaymentOperationPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresPaymentOperationAdapter implements PaymentOperationPort {
    private final SpringDataPaymentOperationRepository repo;
    public PostgresPaymentOperationAdapter(SpringDataPaymentOperationRepository repo) { this.repo=repo; }

    @Override
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public PaymentOperation claim(String key, long loanId, BigDecimal amount, LocalDate date) {
        // La restricción UNIQUE protege frente a dos solicitudes simultáneas.
        // La solicitud concurrente que pierde la carrera recibe conflicto y no invoca Fineract.
        var existing=repo.findByIdempotencyKey(key);
        if (existing.isPresent()) return validate(existing.get(), loanId, amount, date);
        try {
            return repo.saveAndFlush(new PaymentOperationEntity(key, loanId, amount, date)).toDomain();
        } catch (DataIntegrityViolationException ex) {
            throw new PaymentConflictException("Idempotency-Key ya está siendo procesada; consulte GET /payments/{key}");
        }
    }
    private PaymentOperation validate(PaymentOperationEntity entity, long loanId, BigDecimal amount, LocalDate date) {
        if (!entity.matches(loanId, amount, date))
            throw new PaymentConflictException("Idempotency-Key ya utilizada con otros datos");
        if (entity.toDomain().status()==PaymentStatus.PENDING)
            throw new PaymentConflictException("Pago pendiente; no reenviar a Fineract");
        return entity.toDomain();
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public PaymentOperation succeeded(String key, long transactionId) { return update(key, PaymentStatus.SUCCEEDED, transactionId, null); }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public PaymentOperation rejected(String key, String reason) { return update(key, PaymentStatus.REJECTED, null, reason); }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public PaymentOperation unknown(String key, String reason) { return update(key, PaymentStatus.UNKNOWN, null, reason); }
    private PaymentOperation update(String key, PaymentStatus status, Long transactionId, String reason) {
        PaymentOperationEntity entity=repo.findByIdempotencyKey(key).orElseThrow();
        entity.transition(status, transactionId, reason);
        return repo.save(entity).toDomain();
    }
    @Override @Transactional(readOnly=true)
    public PaymentOperation find(String key) {
        return repo.findByIdempotencyKey(key).map(PaymentOperationEntity::toDomain)
                .orElseThrow(() -> new PaymentOperationNotFoundException(key));
    }
}
