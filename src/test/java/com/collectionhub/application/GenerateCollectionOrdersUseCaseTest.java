package com.collectionhub.application;

import com.collectionhub.infrastructure.persistence.IdempotencyConflictException;
import com.collectionhub.domain.model.CollectionOrder;
import com.collectionhub.domain.model.LoanCollectionSnapshot;
import com.collectionhub.domain.port.out.CollectionBatchPort;
import com.collectionhub.domain.port.out.CollectionSchedulePort;
import com.collectionhub.domain.port.out.IdempotencyPort;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class GenerateCollectionOrdersUseCaseTest {

    @Test void filtersPaidAndBeyondHorizonAndKeepsOverdue() {
        CollectionSchedulePort schedule = mock(CollectionSchedulePort.class);
        CollectionBatchPort batches = mock(CollectionBatchPort.class);
        var cutoff = LocalDate.of(2026,11,19);
        when(schedule.fetch(1)).thenReturn(new LoanCollectionSnapshot(1, 5L, "Ana Demo", "PEN", List.of(
                order(1, cutoff.minusDays(5), "100"), order(2, cutoff.plusDays(10), "0"),
                order(3, cutoff.plusDays(20), "100"), order(4, cutoff.plusDays(90), "100"))));
        when(batches.save(any(),any(),anyList(),eq(List.of(1L)))).thenAnswer(inv -> {
            List<CollectionOrder> orders = inv.getArgument(2);
            assertEquals(2, orders.size());
            assertEquals("OVERDUE", orders.get(0).status());
            assertEquals("PENDING", orders.get(1).status());
            assertEquals(3, orders.get(1).installmentNumber());
            return new CollectionBatchPort.BatchResult(10, "COMPLETED", 1, 2, cutoff, cutoff.plusDays(30));
        });
        var result = useCase(schedule, batches).execute(List.of(1L),cutoff,30);
        assertEquals(2,result.ordersGenerated());
    }
    @Test void rejectsDuplicates() {
        var useCase = useCase(mock(CollectionSchedulePort.class), mock(CollectionBatchPort.class));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(List.of(1L,1L),LocalDate.now(),30));
    }
    @Test void partialPaymentUpdatesOnlyAffectedInstallment() {
        var schedule = mock(CollectionSchedulePort.class);
        var batches = mock(CollectionBatchPort.class);
        var cutoff = LocalDate.of(2026, 11, 19);
        var due = LocalDate.of(2027, 1, 19);
        when(schedule.fetch(1L)).thenReturn(new LoanCollectionSnapshot(1L, 1L, "Juan Perez", "PEN", List.of(
                detailedOrder(2, due, "566.19", "0.00"),
                detailedOrder(3, LocalDate.of(2027, 2, 19), "965.21", "100.98"),
                detailedOrder(4, LocalDate.of(2027, 3, 19), "974.86", "91.33"))));
        when(batches.save(eq(cutoff), eq(cutoff.plusDays(120)), anyList(), eq(List.of(1L))))
                .thenAnswer(inv -> {
                    List<CollectionOrder> orders = inv.getArgument(2);
                    assertEquals(3, orders.size());
                    assertEquals(0, new BigDecimal("566.19").compareTo(orders.get(0).totalOutstanding()));
                    assertEquals(0, BigDecimal.ZERO.compareTo(orders.get(0).interestOutstanding()));
                    assertEquals(0, new BigDecimal("2698.57").compareTo(
                            orders.stream().map(CollectionOrder::totalOutstanding).reduce(BigDecimal.ZERO, BigDecimal::add)));
                    return new CollectionBatchPort.BatchResult(4, "COMPLETED", 1, orders.size(), cutoff, cutoff.plusDays(120));
                });
        var result = useCase(schedule, batches).execute(List.of(1L), cutoff, 120);
        assertEquals(3, result.ordersGenerated());
        verify(batches).save(eq(cutoff), eq(cutoff.plusDays(120)), anyList(), eq(List.of(1L)));
    }

    @Test void fullyPaidInstallmentIsExcluded() {
        var schedule = mock(CollectionSchedulePort.class);
        var batches = mock(CollectionBatchPort.class);
        var cutoff = LocalDate.of(2026, 11, 19);
        when(schedule.fetch(1L)).thenReturn(new LoanCollectionSnapshot(1L, 1L, "Juan Perez", "PEN", List.of(
                detailedOrder(2, LocalDate.of(2027, 1, 19), "0", "0"),
                detailedOrder(3, LocalDate.of(2027, 2, 19), "965.21", "100.98"),
                detailedOrder(4, LocalDate.of(2027, 3, 19), "974.86", "91.33"))));
        when(batches.save(any(), any(), anyList(), eq(List.of(1L)))).thenAnswer(inv -> {
            List<CollectionOrder> orders = inv.getArgument(2);
            assertEquals(List.of(3, 4), orders.stream().map(CollectionOrder::installmentNumber).toList());
            assertEquals(0, new BigDecimal("2132.38").compareTo(
                    orders.stream().map(CollectionOrder::totalOutstanding).reduce(BigDecimal.ZERO, BigDecimal::add)));
            return new CollectionBatchPort.BatchResult(5, "COMPLETED", 1, 2, cutoff, cutoff.plusDays(120));
        });
        assertEquals(2, useCase(schedule, batches)
                .execute(List.of(1L), cutoff, 120).ordersGenerated());
    }

    @Test void includesHorizonBoundaryAndExcludesFollowingDay() {
        var schedule = mock(CollectionSchedulePort.class);
        var batches = mock(CollectionBatchPort.class);
        var cutoff = LocalDate.of(2026, 11, 19);
        var end = cutoff.plusDays(120);
        when(schedule.fetch(1L)).thenReturn(new LoanCollectionSnapshot(1L, 1L, "Juan Perez", "PEN", List.of(
                order(1, end, "10"), order(2, end.plusDays(1), "10"))));
        when(batches.save(eq(cutoff), eq(end), anyList(), eq(List.of(1L)))).thenAnswer(inv -> {
            List<CollectionOrder> orders = inv.getArgument(2);
            assertEquals(1, orders.size());
            assertEquals(1, orders.getFirst().installmentNumber());
            return new CollectionBatchPort.BatchResult(6, "COMPLETED", 1, 1, cutoff, end);
        });
        useCase(schedule, batches).execute(List.of(1L), cutoff, 120);
    }

    @Test void combinesOrdersFromMultipleLoansInOneBatch() {
        var schedule = mock(CollectionSchedulePort.class);
        var batches = mock(CollectionBatchPort.class);
        var cutoff = LocalDate.of(2026, 11, 19);
        when(schedule.fetch(1L)).thenReturn(new LoanCollectionSnapshot(1L, 1L, "Juan Perez", "PEN", List.of(
                detailedOrder(2, cutoff.plusDays(5), "100", "10"))));
        when(schedule.fetch(2L)).thenReturn(new LoanCollectionSnapshot(2L, 2L, "Ana Demo", "PEN", List.of(
                new CollectionOrder(null, null, 2L, 2L, "Ana Demo", 1, cutoff.plusDays(10), "PEN",
                        new BigDecimal("200"), new BigDecimal("20"), new BigDecimal("220"), "PENDING"))));
        when(batches.save(any(), any(), anyList(), eq(List.of(1L, 2L)))).thenAnswer(inv -> {
            List<CollectionOrder> orders = inv.getArgument(2);
            assertEquals(List.of(1L, 2L), orders.stream().map(CollectionOrder::loanId).toList());
            return new CollectionBatchPort.BatchResult(7, "COMPLETED", 2, 2, cutoff, cutoff.plusDays(30));
        });
        var result = useCase(schedule, batches).execute(List.of(1L, 2L), cutoff, 30);
        assertEquals(2, result.loansProcessed());
        assertEquals(2, result.ordersGenerated());
        verify(schedule).fetch(1L);
        verify(schedule).fetch(2L);
    }

    @Test void rejectsInvalidRequestsWithoutCallingDependencies() {
        var schedule = mock(CollectionSchedulePort.class);
        var batches = mock(CollectionBatchPort.class);
        var useCase = useCase(schedule, batches);
        var cutoff = LocalDate.of(2026, 11, 19);
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(List.of(), cutoff, 30));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(List.of(-1L), cutoff, 30));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(List.of(1L), null, 30));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(List.of(1L), cutoff, -1));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(List.of(1L), cutoff, 3651));
        verifyNoInteractions(schedule, batches);
    }

    @Test
    void idempotentRequestCreatesBatch() {
        var schedule = mock(CollectionSchedulePort.class);
        var batches = mock(CollectionBatchPort.class);
        var idempotency = mock(IdempotencyPort.class);

        var cutoff = LocalDate.of(2026, 10, 15);

        var expectedBatch = new CollectionBatchPort.BatchResult(
                10L,
                "COMPLETED",
                1,
                1,
                cutoff,
                cutoff.plusDays(31)
        );

        when(schedule.fetch(1L)).thenReturn(
                new LoanCollectionSnapshot(
                        1L,
                        1L,
                        "Ana Demo",
                        "PEN",
                        List.of(order(1, cutoff, "100"))
                )
        );

        when(batches.save(
                eq(cutoff),
                eq(cutoff.plusDays(31)),
                anyList(),
                eq(List.of(1L))
        )).thenReturn(expectedBatch);

        when(idempotency.executeOnce(
                eq("prestamype-test-001"),
                anyString(),
                any()
        )).thenAnswer(invocation -> {
            java.util.function.Supplier<CollectionBatchPort.BatchResult>
                    operation = invocation.getArgument(2);

            return new IdempotencyPort.ExecutionResult(
                    operation.get(),
                    false
            );
        });

        var useCase = new GenerateCollectionOrdersUseCase(
                schedule,
                batches,
                idempotency
        );

        var result = useCase.executeIdempotent(
                List.of(1L),
                cutoff,
                31,
                "prestamype-test-001"
        );

        assertFalse(result.replayed());
        assertEquals(10L, result.batch().batchId());
        assertEquals(1, result.batch().ordersGenerated());

        verify(idempotency, times(1)).executeOnce(
                eq("prestamype-test-001"),
                matches("[0-9a-f]{64}"),
                any()
        );

        verify(schedule, times(1)).fetch(1L);
        verify(batches, times(1)).save(
                eq(cutoff),
                eq(cutoff.plusDays(31)),
                anyList(),
                eq(List.of(1L))
        );
    }
    @Test
    void idempotentReplayDoesNotGenerateAnotherBatch() {
        var schedule = mock(CollectionSchedulePort.class);
        var batches = mock(CollectionBatchPort.class);
        var idempotency = mock(IdempotencyPort.class);

        var cutoff = LocalDate.of(2026, 10, 15);

        var existingBatch = new CollectionBatchPort.BatchResult(
                4L,
                "COMPLETED",
                1,
                1,
                cutoff,
                cutoff.plusDays(31)
        );

        when(idempotency.executeOnce(
                eq("prestamype-test-001"),
                anyString(),
                any()
        )).thenReturn(
                new IdempotencyPort.ExecutionResult(
                        existingBatch,
                        true
                )
        );

        var useCase = new GenerateCollectionOrdersUseCase(
                schedule,
                batches,
                idempotency
        );

        var result = useCase.executeIdempotent(
                List.of(1L),
                cutoff,
                31,
                "prestamype-test-001"
        );

        assertTrue(result.replayed());
        assertEquals(4L, result.batch().batchId());

        verify(idempotency).executeOnce(
                eq("prestamype-test-001"),
                matches("[0-9a-f]{64}"),
                any()
        );

        verifyNoInteractions(schedule, batches);
    }

    @Test
    void idempotentRequestRejectsConflictingParameters() {
        var schedule = mock(CollectionSchedulePort.class);
        var batches = mock(CollectionBatchPort.class);
        var idempotency = mock(IdempotencyPort.class);

        var cutoff = LocalDate.of(2026, 10, 15);

        when(idempotency.executeOnce(
                eq("prestamype-test-001"),
                anyString(),
                any()
        )).thenThrow(new IdempotencyConflictException(
                "Idempotency-Key ya utilizada con parámetros diferentes"
        ));

        var useCase = new GenerateCollectionOrdersUseCase(
                schedule,
                batches,
                idempotency
        );

        var exception = assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.executeIdempotent(
                        List.of(1L),
                        cutoff,
                        60,
                        "prestamype-test-001"
                )
        );

        assertEquals(
                "Idempotency-Key ya utilizada con parámetros diferentes",
                exception.getMessage()
        );

        verifyNoInteractions(schedule, batches);
    }

    @Test
    void idempotentRequestRejectsInvalidParametersBeforeAccessingDependencies() {
        var schedule = mock(CollectionSchedulePort.class);
        var batches = mock(CollectionBatchPort.class);
        var idempotency = mock(IdempotencyPort.class);

        var useCase = new GenerateCollectionOrdersUseCase(
                schedule,
                batches,
                idempotency
        );

        var cutoff = LocalDate.of(2026, 10, 15);

        assertThrows(IllegalArgumentException.class,
                () -> useCase.executeIdempotent(
                        List.of(), cutoff, 31, "test-key"));

        assertThrows(IllegalArgumentException.class,
                () -> useCase.executeIdempotent(
                        List.of(1L, 1L), cutoff, 31, "test-key"));

        assertThrows(IllegalArgumentException.class,
                () -> useCase.executeIdempotent(
                        List.of(-1L), cutoff, 31, "test-key"));

        assertThrows(IllegalArgumentException.class,
                () -> useCase.executeIdempotent(
                        List.of(1L), null, 31, "test-key"));

        assertThrows(IllegalArgumentException.class,
                () -> useCase.executeIdempotent(
                        List.of(1L), cutoff, -1, "test-key"));

        assertThrows(IllegalArgumentException.class,
                () -> useCase.executeIdempotent(
                        List.of(1L), cutoff, 3651, "test-key"));

        verifyNoInteractions(schedule, batches, idempotency);
    }

    @Test
    void fullyPaidLoanIsRegisteredWithoutCollectionOrders() {

        var schedule = mock(CollectionSchedulePort.class);
        var batches = mock(CollectionBatchPort.class);

        var cutoff = LocalDate.of(2026, 10, 15);
        var end = cutoff.plusDays(31);

        when(schedule.fetch(1L)).thenReturn(
                new LoanCollectionSnapshot(
                        1L,
                        1L,
                        "Ana Demo",
                        "PEN",
                        List.of(
                                detailedOrder(1, cutoff, "0", "0"),
                                detailedOrder(2, end, "0", "0")
                        )
                )
        );

        when(batches.save(
                eq(cutoff),
                eq(end),
                anyList(),
                eq(List.of(1L))
        )).thenAnswer(invocation -> {

            List<CollectionOrder> orders =
                    invocation.getArgument(2);

            assertTrue(orders.isEmpty());

            return new CollectionBatchPort.BatchResult(
                    10L,
                    "COMPLETED",
                    1,
                    0,
                    cutoff,
                    end
            );
        });

        var result = useCase(schedule, batches)
                .execute(List.of(1L), cutoff, 31);

        assertEquals(1, result.loansProcessed());
        assertEquals(0, result.ordersGenerated());

        verify(schedule).fetch(1L);

        verify(batches).save(
                eq(cutoff),
                eq(end),
                eq(List.<CollectionOrder>of()),
                eq(List.of(1L))
        );
    }

    private static CollectionOrder detailedOrder(int number, LocalDate date, String principal, String interest) {
        var p = new BigDecimal(principal);
        var i = new BigDecimal(interest);
        return new CollectionOrder(null, null, 1L, 1L, "Juan Perez", number, date, "PEN",
                p, i, p.add(i), "PENDING");
    }

    private static GenerateCollectionOrdersUseCase useCase(
            CollectionSchedulePort schedule, CollectionBatchPort batches) {
        return new GenerateCollectionOrdersUseCase(schedule, batches, mock(IdempotencyPort.class));
    }

    private static CollectionOrder order(int number, LocalDate date, String amount) {
        var principal = new BigDecimal(amount);
        return new CollectionOrder(null,null,1,5L,"Ana Demo",number,date,"PEN",principal,
                BigDecimal.ZERO,principal,"PENDING");
    }
}
