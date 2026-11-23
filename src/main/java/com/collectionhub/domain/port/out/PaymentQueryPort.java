
package com.collectionhub.domain.port.out;

import com.collectionhub.domain.model.FineractPaymentTransaction;
import java.util.List;

public interface PaymentQueryPort {

    List<FineractPaymentTransaction> findTransactionsByLoanId(
            long loanId
    );
}
