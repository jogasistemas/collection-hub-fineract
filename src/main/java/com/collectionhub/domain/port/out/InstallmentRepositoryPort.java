package com.collectionhub.domain.port.out;

import com.collectionhub.domain.model.Installment;
import java.util.List;

public interface InstallmentRepositoryPort {
    void replaceForLoan(long loanId, List<Installment> installments);
    List<Installment> findByLoanId(long loanId);
}
