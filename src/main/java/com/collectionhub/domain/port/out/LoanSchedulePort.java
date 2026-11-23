package com.collectionhub.domain.port.out;

import com.collectionhub.domain.model.Installment;
import java.util.List;

public interface LoanSchedulePort {
    List<Installment> findInstallments(long loanId);
}
