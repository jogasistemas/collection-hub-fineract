
package com.collectionhub.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface PaymentGatewayPort {

    long registerRepayment(
            long loanId,
            BigDecimal amount,
            LocalDate paymentDate,
            String externalId
    );
}
