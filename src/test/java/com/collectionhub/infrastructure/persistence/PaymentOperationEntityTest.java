
package com.collectionhub.infrastructure.persistence;

import com.collectionhub.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PaymentOperationEntityTest {

    @Test
    void successfulPaymentCannotBecomeRejected() {

        var payment = new PaymentOperationEntity(
                "payment-test-001",
                1L,
                new BigDecimal("10.00"),
                LocalDate.of(2026, 11, 20)
        );

        payment.transition(
                PaymentStatus.SUCCEEDED,
                999L,
                null
        );

        assertThrows(
                IllegalStateException.class,
                () -> payment.transition(
                        PaymentStatus.REJECTED,
                        null,
                        "Rechazo posterior"
                )
        );

        assertEquals(
                PaymentStatus.SUCCEEDED,
                payment.toDomain().status()
        );

        assertEquals(
                999L,
                payment.toDomain().fineractTransactionId()
        );
    }

    @Test
    void unknownPaymentCannotBeRetriedAsPending() {

        var payment = new PaymentOperationEntity(
                "payment-test-002",
                1L,
                new BigDecimal("10.00"),
                LocalDate.of(2026, 11, 20)
        );

        payment.transition(
                PaymentStatus.UNKNOWN,
                null,
                "Respuesta incierta de Fineract"
        );

        assertThrows(
                IllegalStateException.class,
                () -> payment.transition(
                        PaymentStatus.PENDING,
                        null,
                        null
                )
        );

        assertEquals(
                PaymentStatus.UNKNOWN,
                payment.toDomain().status()
        );
    }

    @Test
    void successfulPaymentRequiresFineractTransactionId() {

        var payment = new PaymentOperationEntity(
                "payment-test-003",
                1L,
                new BigDecimal("10.00"),
                LocalDate.of(2026, 11, 20)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> payment.transition(
                        PaymentStatus.SUCCEEDED,
                        null,
                        null
                )
        );

        assertEquals(
                PaymentStatus.PENDING,
                payment.toDomain().status()
        );
    }
}
