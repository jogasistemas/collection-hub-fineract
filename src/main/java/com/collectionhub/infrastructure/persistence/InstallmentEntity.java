package com.collectionhub.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "loan_installments", uniqueConstraints = @UniqueConstraint(name = "uk_loan_installment", columnNames = {"loan_id", "installment_number"}))
public class InstallmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "loan_id", nullable = false) private Long loanId;
    @Column(name = "installment_number", nullable = false) private Integer installmentNumber;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "principal_due", nullable = false, precision = 19, scale = 2) private BigDecimal principalDue;
    @Column(name = "interest_due", nullable = false, precision = 19, scale = 2) private BigDecimal interestDue;
    @Column(name = "total_due", nullable = false, precision = 19, scale = 2) private BigDecimal totalDue;
    @Column(name = "total_paid", nullable = false, precision = 19, scale = 2) private BigDecimal totalPaid;
    @Column(name = "outstanding_amount", nullable = false, precision = 19, scale = 2) private BigDecimal outstandingAmount;
    @Column(name = "paid", nullable = false) private boolean paid;
    @Column(name = "synced_at", nullable = false) private Instant syncedAt;
    protected InstallmentEntity() {}
    public InstallmentEntity(long loanId, com.collectionhub.domain.model.Installment installment, Instant syncedAt) {
        this.loanId = loanId;
        update(installment, syncedAt);
    }
    public void update(com.collectionhub.domain.model.Installment installment, Instant at) {
        installmentNumber = installment.installmentNumber();
        dueDate = installment.dueDate();
        principalDue = installment.principalDue();
        interestDue = installment.interestDue();
        totalDue = installment.totalDue();
        totalPaid = installment.totalPaid();
        outstandingAmount = installment.outstandingAmount();
        paid = installment.paid();
        syncedAt = at;
    }
    public Integer getInstallmentNumber() { return installmentNumber; }
    public com.collectionhub.domain.model.Installment toDomain() {
        return new com.collectionhub.domain.model.Installment(installmentNumber, dueDate, principalDue, interestDue, totalDue, totalPaid, outstandingAmount, paid);
    }
}
