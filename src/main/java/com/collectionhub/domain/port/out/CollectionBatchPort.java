package com.collectionhub.domain.port.out;
import com.collectionhub.domain.model.CollectionOrder;
import java.time.LocalDate;
import java.util.List;
public interface CollectionBatchPort {
    BatchResult save(
            LocalDate cutoff,
            LocalDate end,
            List<CollectionOrder> orders,
            List<Long> loanIds
    );
    BatchResult findBatch(long id);
    List<CollectionOrder> findOrders(Long batchId, Long loanId, LocalDate dueBefore, String status);
    record BatchResult(long batchId, String status, int loansProcessed, int ordersGenerated,
                       LocalDate cutoffDate, LocalDate horizonEnd) {}
}
