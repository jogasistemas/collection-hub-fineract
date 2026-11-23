package com.collectionhub.domain.model;
import java.math.BigDecimal;
import java.time.LocalDate;
public record PaymentOperation(String idempotencyKey, long loanId, BigDecimal amount,
        LocalDate paymentDate, PaymentStatus status, Long fineractTransactionId, String failureReason) {}
