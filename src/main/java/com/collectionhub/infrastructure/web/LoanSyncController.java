package com.collectionhub.infrastructure.web;

import com.collectionhub.application.SyncLoanInstallmentsUseCase;
import com.collectionhub.domain.model.Installment;
import com.collectionhub.domain.port.out.InstallmentRepositoryPort;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanSyncController {
    private final SyncLoanInstallmentsUseCase sync;
    private final InstallmentRepositoryPort repository;
    public LoanSyncController(SyncLoanInstallmentsUseCase sync, InstallmentRepositoryPort repository) {
        this.sync = sync;
        this.repository = repository;
    }
    @PostMapping("/{loanId}/installments/sync")
    public List<Installment> sync(@PathVariable long loanId) { return sync.execute(loanId); }
    @GetMapping("/{loanId}/installments/stored")
    public List<Installment> stored(@PathVariable long loanId) {
        if (loanId <= 0) throw new IllegalArgumentException("loanId debe ser mayor que cero");
        return repository.findByLoanId(loanId);
    }
}
