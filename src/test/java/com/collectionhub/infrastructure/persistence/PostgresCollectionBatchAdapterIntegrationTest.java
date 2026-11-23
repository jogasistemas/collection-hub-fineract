package com.collectionhub.infrastructure.persistence;

import com.collectionhub.domain.model.CollectionOrder;
import com.collectionhub.domain.port.out.CollectionBatchPort;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PostgresCollectionBatchAdapterIntegrationTest {

    @Autowired
    private CollectionBatchPort batches;

    private static final long LOAN_ID = 990001L;

    private static final LocalDate CUTOFF =
            LocalDate.of(2026, 10, 15);

    private static final LocalDate END =
            CUTOFF.plusDays(31);

    @Test
    void latestBatchReplacesOutstandingBalance() {

        var first = batches.save(
                CUTOFF,
                END,
                List.of(order("1000", "120")),
                List.of(LOAN_ID)
        );

        var second = batches.save(
                CUTOFF,
                END,
                List.of(order("620", "0")),
                List.of(LOAN_ID)
        );

        var current = batches.findOrders(
                null, LOAN_ID, null, null
        );

        assertEquals(1, current.size());

        assertEquals(
                second.batchId(),
                current.getFirst().batchId()
        );

        assertEquals(
                0,
                new BigDecimal("620")
                        .compareTo(
                                current.getFirst()
                                        .totalOutstanding()
                        )
        );

        var historical = batches.findOrders(
                first.batchId(), LOAN_ID, null, null
        );

        assertEquals(1, historical.size());

        assertEquals(
                0,
                new BigDecimal("1120")
                        .compareTo(
                                historical.getFirst()
                                        .totalOutstanding()
                        )
        );
    }

    @Test
    void fullyPaidLoanDoesNotReturnHistoricalDebt() {

        var first = batches.save(
                CUTOFF,
                END,
                List.of(order("620", "0")),
                List.of(LOAN_ID)
        );

        var second = batches.save(
                CUTOFF,
                END,
                List.of(),
                List.of(LOAN_ID)
        );

        assertEquals(1, second.loansProcessed());
        assertEquals(0, second.ordersGenerated());

        var current = batches.findOrders(
                null, LOAN_ID, null, null
        );

        assertTrue(current.isEmpty());

        var historical = batches.findOrders(
                first.batchId(), LOAN_ID, null, null
        );

        assertEquals(1, historical.size());
    }

    @Test
    void filtersByDueDateAndStatus() {

        var batch = batches.save(
                CUTOFF,
                END,
                List.of(order("1000", "120")),
                List.of(LOAN_ID)
        );

        assertEquals(1, batches.findOrders(
                batch.batchId(),
                LOAN_ID,
                CUTOFF,
                "PENDING"
        ).size());

        assertTrue(batches.findOrders(
                batch.batchId(),
                LOAN_ID,
                CUTOFF.minusDays(1),
                null
        ).isEmpty());

        assertTrue(batches.findOrders(
                batch.batchId(),
                LOAN_ID,
                null,
                "OVERDUE"
        ).isEmpty());
    }

    private CollectionOrder order(
            String principal,
            String interest) {

        var p = new BigDecimal(principal);
        var i = new BigDecimal(interest);

        return new CollectionOrder(
                null,
                null,
                LOAN_ID,
                990001L,
                "Ana Demo Integración",
                1,
                CUTOFF,
                "PEN",
                p,
                i,
                p.add(i),
                "PENDING"
        );
    }
}