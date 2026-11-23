package com.collectionhub.application;
public class PaymentRejectedException extends RuntimeException {
    public PaymentRejectedException(String message) { super(message); }
}
