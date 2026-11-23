package com.collectionhub.application;

import com.collectionhub.domain.model.Installment;
import com.collectionhub.domain.port.out.InstallmentRepositoryPort;
import com.collectionhub.domain.port.out.LoanSchedulePort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncLoanInstallmentsUseCase {
    private final LoanSchedulePort fineract;
    private final InstallmentRepositoryPort repository;

    public SyncLoanInstallmentsUseCase(LoanSchedulePort fineract, InstallmentRepositoryPort repository) {
        this.fineract = fineract;
        this.repository = repository;
    }

    @Transactional
    public List<Installment> execute(long loanId) {
        if (loanId <= 0) throw new IllegalArgumentException("loanId debe ser mayor que cero");
        // Consultar Fineract antes de modificar la base de datos.
        List<Installment> installments = fineract.findInstallments(loanId);
        repository.replaceForLoan(loanId, installments);
        return installments;
    }
}
