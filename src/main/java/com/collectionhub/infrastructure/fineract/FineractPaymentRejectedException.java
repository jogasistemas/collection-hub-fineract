package com.collectionhub.infrastructure.fineract;
public class FineractPaymentRejectedException extends RuntimeException {
    public FineractPaymentRejectedException(String message) { super(message); }
}
