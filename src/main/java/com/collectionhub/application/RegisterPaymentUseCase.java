package com.collectionhub.application;

import com.collectionhub.domain.model.PaymentOperation;
import com.collectionhub.domain.model.PaymentStatus;
import com.collectionhub.domain.port.out.PaymentGatewayPort;
import com.collectionhub.domain.port.out.PaymentOperationPort;
import com.collectionhub.domain.model.PaymentExternalId;
import com.collectionhub.infrastructure.fineract.FineractPaymentRejectedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RegisterPaymentUseCase {
    private static final Logger log = LoggerFactory.getLogger(RegisterPaymentUseCase.class);
    private final PaymentGatewayPort gateway;
    private final PaymentOperationPort operations;
    private final SyncLoanInstallmentsUseCase sync;

    public RegisterPaymentUseCase(PaymentGatewayPort gateway, PaymentOperationPort operations,
                                  SyncLoanInstallmentsUseCase sync) {
        this.gateway = gateway;
        this.operations = operations;
        this.sync = sync;
    }

    public PaymentOperation execute(String key, long loanId, BigDecimal amount, LocalDate date) {
        if (loanId <= 0) throw new IllegalArgumentException("loanId debe ser mayor que cero");
        if (key == null || !key.matches("[a-zA-Z0-9_-]{8,128}"))
            throw new IllegalArgumentException("Idempotency-Key debe contener 8-128 caracteres alfanuméricos, _ o -");
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2)
            throw new IllegalArgumentException("amount debe ser positivo y tener máximo dos decimales");
        if (date == null) throw new IllegalArgumentException("paymentDate es obligatorio");
        PaymentOperation operation = operations.claim(key, loanId, amount, date);
        if (operation.status() == PaymentStatus.SUCCEEDED) return operation;
        if (operation.status() != PaymentStatus.PENDING)
            throw new PaymentConflictException("Operación " + operation.status() + ": requiere revisión antes de reintentar");
        // claim() garantiza que solamente la solicitud que creó la fila alcanza esta llamada.
        long transactionId;
        try {
            transactionId = gateway.registerRepayment(
                    loanId,
                    amount,
                    date,
                    PaymentExternalId.fromIdempotencyKey(key)
            );
        } catch (FineractPaymentRejectedException ex) {
            operations.rejected(key, ex.getMessage());
            throw new PaymentRejectedException(ex.getMessage());
        } catch (Exception ex) {
            // No reintentar automáticamente: la operación puede haber llegado a Fineract.
            operations.unknown(key, "Resultado no confirmado; conciliar en Fineract antes de actuar");
            throw new PaymentUnknownException("Resultado de pago incierto. Consultar el estado por Idempotency-Key y conciliar en Fineract; no reintentar con otra clave.");
        }
        // Si falla esta escritura, la operación queda PENDING y exige conciliación.
        PaymentOperation succeeded = operations.succeeded(key, transactionId);
        // El pago ya fue confirmado y persistido: un error de sincronización
        // no debe convertirlo en un pago fallido ni disparar un reintento.
        try {
            sync.execute(loanId);
        } catch (Exception ex) {
            log.warn("Pago {} confirmado (transacción Fineract {}), pero falló sync de cuotas del préstamo {}. "
                    + "Ejecutar POST /api/v1/loans/{}/installments/sync para conciliar.",
                    key, transactionId, loanId, loanId, ex);
        }
        return succeeded;
    }

    public PaymentOperation find(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key obligatorio");
        return operations.find(key);
    }
}
