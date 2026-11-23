package com.collectionhub.infrastructure.persistence;

import com.collectionhub.domain.port.out.CollectionBatchPort;
import com.collectionhub.domain.port.out.IdempotencyPort;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PostgresIdempotencyAdapterIntegrationTest {

    @Autowired
    private IdempotencyPort idempotency;

    @Autowired
    private CollectionBatchPort batches;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void repeatedRequestReturnsSameBatchWithoutExecutingAgain() {

        String key = "integration-" + UUID.randomUUID();
        String hash = "test-request-hash";

        LocalDate cutoff = LocalDate.of(2026, 10, 15);
        LocalDate end = cutoff.plusDays(31);

        AtomicInteger executions = new AtomicInteger();

        Supplier<CollectionBatchPort.BatchResult> operation = () -> {
            executions.incrementAndGet();

            return batches.save(
                    cutoff,
                    end,
                    List.of(),
                    List.of(1L)
            );
        };

        Long batchId = null;

        try {
            var first = idempotency.executeOnce(
                    key,
                    hash,
                    operation
            );

            batchId = first.batch().batchId();

            var second = idempotency.executeOnce(
                    key,
                    hash,
                    operation
            );

            assertFalse(first.replayed());
            assertTrue(second.replayed());

            assertEquals(
                    first.batch().batchId(),
                    second.batch().batchId()
            );

            assertEquals(1, executions.get());

            Integer keyCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM idempotency_keys WHERE idempotency_key = ?",
                    Integer.class,
                    key
            );

            assertEquals(1, keyCount);

        } finally {
            // Limpiar los registros creados por la prueba.
            jdbc.update(
                    "DELETE FROM idempotency_keys WHERE idempotency_key = ?",
                    key
            );

            if (batchId != null) {
                jdbc.update(
                        "DELETE FROM collection_orders WHERE batch_id = ?",
                        batchId
                );

                jdbc.update(
                        "DELETE FROM sync_batch_loans WHERE batch_id = ?",
                        batchId
                );

                jdbc.update(
                        "DELETE FROM sync_batches WHERE id = ?",
                        batchId
                );
            }
        }
    }

    @Test
    void failedOperationRollsBackBatchAndIdempotencyKey() {

        String key = "rollback-" + UUID.randomUUID();
        String hash = "rollback-request-hash";

        LocalDate cutoff = LocalDate.of(2026, 10, 15);
        LocalDate end = cutoff.plusDays(31);

        var createdBatchId =
                new java.util.concurrent.atomic.AtomicReference<Long>();

        assertThrows(InvalidDataAccessApiUsageException.class, () ->
                idempotency.executeOnce(
                        key,
                        hash,
                        () -> {
                            var batch = batches.save(
                                    cutoff,
                                    end,
                                    List.of(),
                                    List.of(1L)
                            );
                            createdBatchId.set(batch.batchId());

                            // Simulamos un fallo después de guardar el lote.
                            throw new IllegalStateException(
                                    "Error simulado durante la generación del lote"
                            );
                        }
                )
        );

        assertNotNull(createdBatchId.get());

        Integer keyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_keys WHERE idempotency_key = ?",
                Integer.class,
                key
        );

        Integer batchCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sync_batches WHERE id = ?",
                Integer.class,
                createdBatchId.get()
        );

        assertEquals(0, keyCount);
        assertEquals(0, batchCount);
    }

    @Test
    void concurrentRequestsCreateOnlyOneBatch() throws Exception {

        String key = "concurrent-" + UUID.randomUUID();
        String hash = "concurrent-request-hash";

        LocalDate cutoff = LocalDate.of(2026, 10, 15);
        LocalDate end = cutoff.plusDays(31);

        AtomicInteger executions = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Long batchId = null;

        try (var executor = Executors.newFixedThreadPool(2)) {

            java.util.concurrent.Callable<IdempotencyPort.ExecutionResult> task =
                    () -> {
                        ready.countDown();

                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Tiempo de espera agotado"
                            );
                        }

                        return idempotency.executeOnce(
                                key,
                                hash,
                                () -> {
                                    executions.incrementAndGet();

                                    try {
                                        Thread.sleep(300);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new IllegalStateException(e);
                                    }

                                    return batches.save(
                                            cutoff,
                                            end,
                                            List.of(),
                                            List.of(1L)
                                    );
                                }
                        );
                    };

            var firstFuture = executor.submit(task);
            var secondFuture = executor.submit(task);

            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();

                var first = firstFuture.get(15, TimeUnit.SECONDS);
                var second = secondFuture.get(15, TimeUnit.SECONDS);

                batchId = first.batch().batchId();

                assertEquals(
                        first.batch().batchId(),
                        second.batch().batchId()
                );

                assertEquals(1, executions.get());

                assertNotEquals(first.replayed(), second.replayed());

                Integer keyCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM idempotency_keys WHERE idempotency_key = ?",
                        Integer.class,
                        key
                );

                assertEquals(1, keyCount);

            } finally {
                start.countDown();

                firstFuture.cancel(true);
                secondFuture.cancel(true);

                // Esperamos a que terminen antes de limpiar los datos.
                executor.shutdown();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Los hilos no finalizaron a tiempo"
                    );
                }
            }

        } finally {
            jdbc.update(
                    "DELETE FROM idempotency_keys WHERE idempotency_key = ?",
                    key
            );

            if (batchId != null) {
                jdbc.update(
                        "DELETE FROM collection_orders WHERE batch_id = ?",
                        batchId
                );

                jdbc.update(
                        "DELETE FROM sync_batch_loans WHERE batch_id = ?",
                        batchId
                );

                jdbc.update(
                        "DELETE FROM sync_batches WHERE id = ?",
                        batchId
                );
            }
        }
    }
}