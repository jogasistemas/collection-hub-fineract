package com.collectionhub.infrastructure.persistence;

import com.collectionhub.domain.model.PaymentOperation;
import com.collectionhub.domain.model.PaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "payment_operations")
public class PaymentOperationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="idempotency_key", nullable=false, unique=true, length=128) private String idempotencyKey;
    @Column(name="loan_id", nullable=false) private long loanId;
    @Column(name="amount", nullable=false, precision=19, scale=2) private BigDecimal amount;
    @Column(name="payment_date", nullable=false) private LocalDate paymentDate;
    @Enumerated(EnumType.STRING) @Column(name="status", nullable=false) private PaymentStatus status;
    @Column(name="fineract_transaction_id") private Long fineractTransactionId;
    @Column(name="failure_reason", length=500) private String failureReason;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    protected PaymentOperationEntity() {}
    public PaymentOperationEntity(String key, long loanId, BigDecimal amount, LocalDate date) {
        this.idempotencyKey=key;this.loanId=loanId;this.amount=amount;this.paymentDate=date;
        this.status=PaymentStatus.PENDING;this.createdAt=Instant.now();this.updatedAt=createdAt;
    }

    public void transition(
            PaymentStatus newStatus,
            Long transactionId,
            String reason) {

        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "No se puede cambiar una operación en estado "
                            + this.status
            );
        }

        if (newStatus == null || newStatus == PaymentStatus.PENDING) {
            throw new IllegalArgumentException(
                    "La transición requiere un estado de resultado válido"
            );
        }

        if (newStatus == PaymentStatus.SUCCEEDED
                && (transactionId == null || transactionId <= 0)) {
            throw new IllegalArgumentException(
                    "Un pago exitoso requiere un ID de transacción Fineract"
            );
        }

        if (newStatus != PaymentStatus.SUCCEEDED
                && transactionId != null) {
            throw new IllegalArgumentException(
                    "Solo un pago exitoso puede registrar un ID de Fineract"
            );
        }

        this.status = newStatus;
        this.fineractTransactionId = transactionId;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public boolean matches(long loanId, BigDecimal amount, LocalDate date) {
        return this.loanId==loanId && this.amount.compareTo(amount)==0 && this.paymentDate.equals(date);
    }
    public PaymentOperation toDomain() {
        return new PaymentOperation(idempotencyKey, loanId, amount, paymentDate, status, fineractTransactionId, failureReason);
    }
}
