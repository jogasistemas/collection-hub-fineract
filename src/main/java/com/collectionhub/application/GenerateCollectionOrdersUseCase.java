package com.collectionhub.application;
import com.collectionhub.domain.model.CollectionOrder;
import com.collectionhub.domain.port.out.CollectionBatchPort;
import com.collectionhub.domain.port.out.CollectionSchedulePort;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.collectionhub.domain.port.out.IdempotencyPort;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class GenerateCollectionOrdersUseCase {
    private final CollectionSchedulePort schedule;
    private final CollectionBatchPort batches;
    private final IdempotencyPort idempotency;

    public GenerateCollectionOrdersUseCase(
            CollectionSchedulePort schedule,
            CollectionBatchPort batches,
            IdempotencyPort idempotency) {

        this.schedule = schedule;
        this.batches = batches;
        this.idempotency = idempotency;
    }
    public CollectionBatchPort.BatchResult execute(List<Long> loanIds, LocalDate cutoffDate, int horizonDays) {
        validateRequest(loanIds, cutoffDate, horizonDays);
        LocalDate end = cutoffDate.plusDays(horizonDays);
        List<CollectionOrder> orders = new ArrayList<>();
        for (long loanId : loanIds) {
            var snapshot = schedule.fetch(loanId);
            for (var order : snapshot.orders()) {
                if (!order.dueDate().isAfter(end) && order.totalOutstanding().signum() > 0) {
                    orders.add(new CollectionOrder(null, null, loanId, order.clientId(), order.clientName(),
                            order.installmentNumber(), order.dueDate(), order.currency(),
                            order.principalOutstanding(), order.interestOutstanding(), order.totalOutstanding(),
                            order.dueDate().isBefore(cutoffDate) ? "OVERDUE" : "PENDING"));
                }
            }
        }
        // Las consultas remotas terminan antes de iniciar la transacción de persistencia.
        return batches.save(cutoffDate, end, orders, loanIds);
    }

    public IdempotencyPort.ExecutionResult executeIdempotent(
            List<Long> loanIds,
            LocalDate cutoffDate,
            int horizonDays,
            String idempotencyKey) {

        validateRequest(loanIds, cutoffDate, horizonDays);

        // Se calcula una huella estable de los parámetros.
        String requestHash = calculateHash(
                loanIds,
                cutoffDate,
                horizonDays
        );

        return idempotency.executeOnce(
                idempotencyKey,
                requestHash,
                () -> execute(loanIds, cutoffDate, horizonDays)
        );
    }

    private String calculateHash(
            List<Long> loanIds,
            LocalDate cutoffDate,
            int horizonDays) {

        if (loanIds == null || cutoffDate == null) {
            throw new IllegalArgumentException("Parámetros inválidos");
        }

        // El orden de los préstamos no altera el contenido lógico.
        String canonicalRequest =
                loanIds.stream().sorted().toList()
                        + "|" + cutoffDate
                        + "|" + horizonDays;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    canonicalRequest.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "No se pudo calcular SHA-256", e
            );
        }
    }

    private void validateRequest(
            List<Long> loanIds,
            LocalDate cutoffDate,
            int horizonDays) {

        if (loanIds == null
                || loanIds.isEmpty()
                || loanIds.size() > 100
                || loanIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(loanIds).size() != loanIds.size()) {

            throw new IllegalArgumentException(
                    "loanIds debe contener de 1 a 100 IDs positivos sin duplicados"
            );
        }

        if (cutoffDate == null || horizonDays < 0 || horizonDays > 3650) {
            throw new IllegalArgumentException(
                    "Fecha de corte u horizonte inválido"
            );
        }
    }
}
