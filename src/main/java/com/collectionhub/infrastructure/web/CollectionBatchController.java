package com.collectionhub.infrastructure.web;
import com.collectionhub.application.GenerateCollectionOrdersUseCase;
import com.collectionhub.domain.model.CollectionOrder;
import com.collectionhub.domain.port.out.CollectionBatchPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1")
public class CollectionBatchController {
    private final GenerateCollectionOrdersUseCase generate;
    private final CollectionBatchPort batches;
    public CollectionBatchController(GenerateCollectionOrdersUseCase generate, CollectionBatchPort batches) {
        this.generate = generate; this.batches = batches;
    }
    public record CreateBatchRequest(@NotEmpty List<@NotNull @Min(1) Long> loanIds,
            @NotNull LocalDate cutoffDate, @Min(0) @Max(3650) int horizonDays) {}
    @PostMapping("/collection-batches")
    public ResponseEntity<CollectionBatchPort.BatchResult> create(
            @Valid @RequestBody CreateBatchRequest request,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String idempotencyKey) {

        if (idempotencyKey == null) {
            var result = generate.execute(
                    request.loanIds(),
                    request.cutoffDate(),
                    request.horizonDays()
            );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(result);
        }

        var result = generate.executeIdempotent(
                request.loanIds(),
                request.cutoffDate(),
                request.horizonDays(),
                idempotencyKey
        );

        return ResponseEntity
                .status(result.replayed()
                        ? HttpStatus.OK
                        : HttpStatus.CREATED)
                .body(result.batch());
    }
    @GetMapping("/collection-batches/{batchId}")
    public CollectionBatchPort.BatchResult batch(@PathVariable long batchId) { return batches.findBatch(batchId); }
    @GetMapping("/collection-orders")
    public List<CollectionOrder> orders(@RequestParam(required=false) Long batchId,
            @RequestParam(required=false) Long loanId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dueBefore,
            @RequestParam(required=false) String status) {
        if (batchId != null && batchId <= 0 || loanId != null && loanId <= 0) throw new IllegalArgumentException("ID inválido");
        if (status != null && !List.of("PENDING", "OVERDUE").contains(status)) throw new IllegalArgumentException("Estado inválido");
        return batches.findOrders(batchId, loanId, dueBefore, status);
    }


}
