package com.collectionhub.infrastructure.fineract;

public class FineractIntegrationException extends RuntimeException {
    public FineractIntegrationException(String message) {super(message);}
    public FineractIntegrationException(String message, Throwable cause) {super(message, cause);}
}
