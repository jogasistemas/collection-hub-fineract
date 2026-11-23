package com.collectionhub.domain.model;
import java.math.BigDecimal;
import java.time.LocalDate;
public record CollectionOrder(Long id, Long batchId, long loanId, Long clientId, String clientName,
        int installmentNumber, LocalDate dueDate, String currency,
        BigDecimal principalOutstanding, BigDecimal interestOutstanding,
        BigDecimal totalOutstanding, String status) {}
