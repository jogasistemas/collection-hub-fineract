
package com.collectionhub.application;

import com.collectionhub.domain.model.PaymentOperation;
import com.collectionhub.domain.model.PaymentStatus;
import com.collectionhub.domain.port.out.PaymentGatewayPort;
import com.collectionhub.domain.port.out.PaymentOperationPort;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegisterPaymentUseCaseTest {

    private final PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
    private final PaymentOperationPort repo = mock(PaymentOperationPort.class);
    private final SyncLoanInstallmentsUseCase sync = mock(SyncLoanInstallmentsUseCase.class);

    private final RegisterPaymentUseCase useCase =
            new RegisterPaymentUseCase(gateway, repo, sync);

    private final String key = "payment-00001";
    private final LocalDate date = LocalDate.of(2026, 11, 19);
    private final BigDecimal amount = new BigDecimal("1066.19");

    @Test
    void registersAndStoresTransactionId() {
        when(repo.claim(key, 1, amount, date))
                .thenReturn(op(PaymentStatus.PENDING));

        when(gateway.registerRepayment(
                eq(1L), eq(amount), eq(date), anyString()
        )).thenReturn(99L);

        when(repo.succeeded(key, 99L))
                .thenReturn(new PaymentOperation(
                        key, 1, amount, date,
                        PaymentStatus.SUCCEEDED, 99L, null
                ));

        PaymentOperation result = useCase.execute(key, 1, amount, date);

        assertEquals(99L, result.fineractTransactionId());

        verify(gateway).registerRepayment(
                1L, amount, date, expectedExternalId(key)
        );

        verify(sync).execute(1L);
    }

    @Test
    void syncFailureDoesNotInvalidateSuccessfulPayment() {
        when(repo.claim(key, 1, amount, date))
                .thenReturn(op(PaymentStatus.PENDING));

        when(gateway.registerRepayment(
                eq(1L), eq(amount), eq(date), anyString()
        )).thenReturn(99L);

        when(repo.succeeded(key, 99L))
                .thenReturn(new PaymentOperation(
                        key, 1, amount, date,
                        PaymentStatus.SUCCEEDED, 99L, null
                ));

        doThrow(new RuntimeException("Fineract unavailable"))
                .when(sync).execute(1L);

        assertEquals(
                PaymentStatus.SUCCEEDED,
                useCase.execute(key, 1, amount, date).status()
        );

        verify(repo).succeeded(key, 99L);
    }

    @Test
    void successfulReplayDoesNotCallGateway() {
        when(repo.claim(key, 1, amount, date))
                .thenReturn(op(PaymentStatus.SUCCEEDED));

        assertEquals(
                PaymentStatus.SUCCEEDED,
                useCase.execute(key, 1, amount, date).status()
        );

        verifyNoInteractions(gateway, sync);
    }

    @Test
    void unknownResultIsNeverRetriedAutomatically() {
        when(repo.claim(key, 1, amount, date))
                .thenReturn(op(PaymentStatus.PENDING));

        when(gateway.registerRepayment(
                eq(1L), eq(amount), eq(date), anyString()
        )).thenThrow(new RuntimeException("timeout"));

        assertThrows(
                PaymentUnknownException.class,
                () -> useCase.execute(key, 1, amount, date)
        );

        verify(repo).unknown(eq(key), anyString());

        verify(gateway, times(1)).registerRepayment(
                1L, amount, date, expectedExternalId(key)
        );
    }

    @Test
    void externalIdIsDeterministicAndWithinFineractLimit() {
        when(repo.claim(key, 1, amount, date))
                .thenReturn(op(PaymentStatus.PENDING));

        when(gateway.registerRepayment(
                eq(1L), eq(amount), eq(date), anyString()
        )).thenReturn(99L);

        when(repo.succeeded(key, 99L))
                .thenReturn(new PaymentOperation(
                        key, 1, amount, date,
                        PaymentStatus.SUCCEEDED, 99L, null
                ));

        useCase.execute(key, 1, amount, date);

        String externalId = expectedExternalId(key);

        assertEquals(67, externalId.length());
        assertTrue(externalId.startsWith("CH-"));

        verify(gateway).registerRepayment(
                1L, amount, date, externalId
        );
    }

    private String expectedExternalId(String idempotencyKey) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));

            return "CH-" + HexFormat.of().formatHex(hash);

        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private PaymentOperation op(PaymentStatus status) {
        return new PaymentOperation(
                key, 1, amount, date, status, null, null
        );
    }
}
