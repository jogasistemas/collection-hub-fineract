package com.collectionhub.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Installment(
        Integer installmentNumber,
        LocalDate dueDate,
        BigDecimal principalDue,
        BigDecimal interestDue,
        BigDecimal totalDue,
        BigDecimal totalPaid,
        BigDecimal outstandingAmount,
        boolean paid
) {}
