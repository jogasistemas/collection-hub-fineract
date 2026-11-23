package com.collectionhub.infrastructure.fineract;

public class LoanNotFoundException extends RuntimeException {
    public LoanNotFoundException(long loanId) {super("Préstamo no encontrado: " + loanId);}
}
