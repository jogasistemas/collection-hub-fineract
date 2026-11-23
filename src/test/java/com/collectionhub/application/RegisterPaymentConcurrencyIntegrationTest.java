
package com.collectionhub.application;

import com.collectionhub.domain.model.PaymentStatus;
import com.collectionhub.domain.port.out.PaymentGatewayPort;
import com.collectionhub.infrastructure.persistence.SpringDataPaymentOperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class RegisterPaymentConcurrencyIntegrationTest {

    @Autowired
    private RegisterPaymentUseCase useCase;

    @Autowired
    private SpringDataPaymentOperationRepository repo;

    @MockBean
    private PaymentGatewayPort gateway;

    @MockBean
    private SyncLoanInstallmentsUseCase sync;

    @Test
    void concurrentRequestsCallFineractOnlyOnce() throws Exception {

        String key = "payment-" + UUID.randomUUID();
        long loanId = 1L;
        BigDecimal amount = new BigDecimal("10.00");
        LocalDate date = LocalDate.of(2026, 11, 20);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        when(gateway.registerRepayment(
                eq(loanId),
                eq(amount),
                eq(date),
                anyString()
        )).thenReturn(999L);

        try {
            try (var executor = Executors.newFixedThreadPool(2)) {

                Callable<PaymentStatus> task = () -> {
                    ready.countDown();

                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timeout");
                    }

                    try {
                        return useCase.execute(
                                key, loanId, amount, date
                        ).status();

                    } catch (PaymentConflictException ex) {
                        return null;
                    }
                };

                Future<PaymentStatus> first = executor.submit(task);
                Future<PaymentStatus> second = executor.submit(task);

                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();

                PaymentStatus status1 =
                        first.get(15, TimeUnit.SECONDS);

                PaymentStatus status2 =
                        second.get(15, TimeUnit.SECONDS);

                assertTrue(
                        status1 == PaymentStatus.SUCCEEDED ||
                                status2 == PaymentStatus.SUCCEEDED
                );
            }

            verify(gateway, times(1))
                    .registerRepayment(
                            eq(loanId),
                            eq(amount),
                            eq(date),
                            anyString()
                    );

            var saved = repo.findByIdempotencyKey(key);

            assertTrue(saved.isPresent());
            assertEquals(
                    PaymentStatus.SUCCEEDED,
                    saved.get().toDomain().status()
            );

        } finally {
            start.countDown();
            repo.findByIdempotencyKey(key)
                    .ifPresent(repo::delete);
        }
    }
}
