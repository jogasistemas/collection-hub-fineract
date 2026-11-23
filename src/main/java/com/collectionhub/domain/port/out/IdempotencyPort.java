package com.collectionhub.domain.port.out;

import java.util.Optional;
import java.util.function.Supplier;

public interface IdempotencyPort {

    Optional<StoredRequest> find(String key);

    ExecutionResult executeOnce(
            String key,
            String requestHash,
            Supplier<CollectionBatchPort.BatchResult> operation
    );

    record StoredRequest(
            String requestHash,
            long batchId
    ) {}

    record ExecutionResult(
            CollectionBatchPort.BatchResult batch,
            boolean replayed
    ) {}
}
