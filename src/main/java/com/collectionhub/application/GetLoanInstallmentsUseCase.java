package com.collectionhub.application;

import com.collectionhub.domain.model.Installment;
import com.collectionhub.domain.port.out.LoanSchedulePort;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GetLoanInstallmentsUseCase {
    private final LoanSchedulePort loanSchedulePort;

    public GetLoanInstallmentsUseCase(LoanSchedulePort loanSchedulePort) {
        this.loanSchedulePort = loanSchedulePort;
    }

    public List<Installment> execute(long loanId) {
        if (loanId <= 0) throw new IllegalArgumentException("loanId debe ser mayor que cero");
        return loanSchedulePort.findInstallments(loanId);
    }
}
