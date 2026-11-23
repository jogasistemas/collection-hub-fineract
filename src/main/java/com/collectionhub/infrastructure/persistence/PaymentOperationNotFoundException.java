package com.collectionhub.infrastructure.persistence;
public class PaymentOperationNotFoundException extends RuntimeException {
    public PaymentOperationNotFoundException(String key) { super("Operación de pago no encontrada: " + key); }
}
