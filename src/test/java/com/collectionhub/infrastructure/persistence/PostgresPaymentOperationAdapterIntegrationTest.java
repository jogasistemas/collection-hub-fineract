
package com.collectionhub.infrastructure.persistence;

import com.collectionhub.application.PaymentConflictException;
import com.collectionhub.domain.model.PaymentStatus;
import com.collectionhub.domain.port.out.PaymentOperationPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PostgresPaymentOperationAdapterIntegrationTest {

    @Autowired
    private PaymentOperationPort operations;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentRequestsCannotClaimSamePaymentTwice() throws Exception {

        String key = "payment-" + UUID.randomUUID();
        long loanId = 1L;
        BigDecimal amount = new BigDecimal("10.00");
        LocalDate date = LocalDate.of(2026, 11, 20);

        AtomicInteger successfulClaims = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            try (var executor = Executors.newFixedThreadPool(2)) {

                Runnable task = () -> {
                    ready.countDown();

                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Tiempo de espera agotado"
                            );
                        }

                        var result = operations.claim(
                                key, loanId, amount, date
                        );

                        assertEquals(
                                PaymentStatus.PENDING,
                                result.status()
                        );

                        successfulClaims.incrementAndGet();

                    } catch (PaymentConflictException ex) {
                        conflicts.incrementAndGet();

                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                };

                var first = executor.submit(task);
                var second = executor.submit(task);

                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();

                first.get(15, TimeUnit.SECONDS);
                second.get(15, TimeUnit.SECONDS);
            }

            assertEquals(1, successfulClaims.get());
            assertEquals(1, conflicts.get());

            Integer count = jdbc.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM payment_operations
                    WHERE idempotency_key = ?
                    """,
                    Integer.class,
                    key
            );

            assertEquals(1, count);

        } finally {
            start.countDown();

            jdbc.update(
                    "DELETE FROM payment_operations WHERE idempotency_key = ?",
                    key
            );
        }
    }
}
