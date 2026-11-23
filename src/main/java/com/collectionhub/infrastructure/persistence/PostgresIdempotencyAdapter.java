package com.collectionhub.infrastructure.persistence;

import com.collectionhub.domain.port.out.CollectionBatchPort;
import com.collectionhub.domain.port.out.IdempotencyPort;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresIdempotencyAdapter implements IdempotencyPort {

    private final JdbcTemplate jdbc;
    private final CollectionBatchPort batches;

    public PostgresIdempotencyAdapter(
            JdbcTemplate jdbc,
            CollectionBatchPort batches) {
        this.jdbc = jdbc;
        this.batches = batches;
    }

    @Override
    public Optional<StoredRequest> find(String key) {
        return jdbc.query(
            """
            SELECT request_hash, batch_id
            FROM idempotency_keys
            WHERE idempotency_key = ?
            """,
            (rs, rowNum) -> new StoredRequest(
                rs.getString("request_hash"),
                rs.getLong("batch_id")
            ),
            key
        ).stream().findFirst();
    }

    @Override
    @Transactional
    public ExecutionResult executeOnce(
            String key,
            String requestHash,
            Supplier<CollectionBatchPort.BatchResult> operation) {

        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException(
                "Idempotency-Key debe contener entre 1 y 128 caracteres"
            );
        }

        // Bloqueo transaccional asociado a la clave.
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> {
                    // El bloqueo se adquiere al ejecutar la consulta.
                    return null;
                },
                key
        );

        Optional<StoredRequest> existing = find(key);

        if (existing.isPresent()) {
            StoredRequest stored = existing.get();

            if (!stored.requestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                    "Idempotency-Key ya utilizada con parámetros diferentes"
                );
            }

            return new ExecutionResult(
                batches.findBatch(stored.batchId()),
                true
            );
        }

        // Se ejecuta únicamente cuando la clave no existe.
        CollectionBatchPort.BatchResult result = operation.get();

        jdbc.update(
            """
            INSERT INTO idempotency_keys
                (idempotency_key, request_hash, batch_id)
            VALUES (?, ?, ?)
            """,
            key,
            requestHash,
            result.batchId()
        );

        return new ExecutionResult(result, false);
    }
}
