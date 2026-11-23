package com.collectionhub.infrastructure.web;

import com.collectionhub.application.GetLoanInstallmentsUseCase;
import com.collectionhub.domain.model.Installment;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanInstallmentsController {
    private final GetLoanInstallmentsUseCase useCase;

    public LoanInstallmentsController(GetLoanInstallmentsUseCase useCase) {this.useCase = useCase;}

    @GetMapping("/{loanId}/installments")
    public List<Installment> getInstallments(@PathVariable long loanId) {
        return useCase.execute(loanId);
    }
}
