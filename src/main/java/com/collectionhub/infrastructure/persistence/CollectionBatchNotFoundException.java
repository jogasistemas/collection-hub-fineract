package com.collectionhub.infrastructure.persistence;
public class CollectionBatchNotFoundException extends RuntimeException {
    public CollectionBatchNotFoundException(long id) { super("Lote no encontrado: " + id); }
}
