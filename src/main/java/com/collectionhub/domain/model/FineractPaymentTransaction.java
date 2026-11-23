package com.collectionhub.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FineractPaymentTransaction(
        long transactionId,
        long loanId,
        BigDecimal amount,
        LocalDate transactionDate,
        boolean reversed,
        String externalId
) {
    // Compatibilidad con el constructor anterior
    public FineractPaymentTransaction(
            long transactionId,
            long loanId,
            BigDecimal amount,
            LocalDate transactionDate,
            boolean reversed
    ) {
        this(
                transactionId,
                loanId,
                amount,
                transactionDate,
                reversed,
                null
        );
    }
}