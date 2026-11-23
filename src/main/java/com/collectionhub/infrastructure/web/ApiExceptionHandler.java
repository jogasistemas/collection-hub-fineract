package com.collectionhub.infrastructure.web;

import com.collectionhub.application.PaymentConflictException;
import com.collectionhub.application.PaymentRejectedException;
import com.collectionhub.application.PaymentUnknownException;
import com.collectionhub.infrastructure.persistence.IdempotencyConflictException;
import com.collectionhub.infrastructure.fineract.FineractIntegrationException;
import com.collectionhub.infrastructure.fineract.LoanNotFoundException;
import com.collectionhub.infrastructure.persistence.PaymentOperationNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({LoanNotFoundException.class, PaymentOperationNotFoundException.class, com.collectionhub.infrastructure.persistence.CollectionBatchNotFoundException.class})
    ResponseEntity<Map<String,String>> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error",e.getMessage()));
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String,String>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Datos inválidos: " + e.getMessage()));
    }
    @ExceptionHandler({
            PaymentConflictException.class,
            IdempotencyConflictException.class
    })
    ResponseEntity<Map<String, String>> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }
    @ExceptionHandler(PaymentRejectedException.class)
    ResponseEntity<Map<String,String>> rejected(PaymentRejectedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error",e.getMessage()));
    }
    @ExceptionHandler(PaymentUnknownException.class)
    ResponseEntity<Map<String,String>> uncertain(PaymentUnknownException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error",e.getMessage()));
    }
    @ExceptionHandler(FineractIntegrationException.class)
    ResponseEntity<Map<String,String>> gatewayError(FineractIntegrationException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error",e.getMessage()));
    }
}
