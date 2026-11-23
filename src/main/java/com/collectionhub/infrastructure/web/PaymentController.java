package com.collectionhub.infrastructure.web;

import com.collectionhub.application.RegisterPaymentUseCase;
import com.collectionhub.domain.model.PaymentOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final RegisterPaymentUseCase payments;
    public PaymentController(RegisterPaymentUseCase payments) { this.payments=payments; }
    public record PaymentRequest(@NotNull @DecimalMin("0.01") @Digits(integer=17, fraction=2) BigDecimal amount,
                                 @NotNull LocalDate paymentDate) {}
    @PostMapping("/loans/{loanId}/payments")
    public ResponseEntity<PaymentOperation> pay(@PathVariable long loanId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(payments.execute(key, loanId, request.amount(), request.paymentDate()));
    }
    @GetMapping("/payments/{key}")
    public PaymentOperation status(@PathVariable String key) { return payments.find(key); }
}
