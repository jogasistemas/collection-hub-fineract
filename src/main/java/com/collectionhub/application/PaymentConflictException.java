package com.collectionhub.application;
public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException(String message) { super(message); }
}
