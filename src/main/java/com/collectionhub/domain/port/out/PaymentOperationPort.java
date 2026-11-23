package com.collectionhub.domain.port.out;
import com.collectionhub.domain.model.PaymentOperation;
import java.math.BigDecimal;
import java.time.LocalDate;
public interface PaymentOperationPort {
    PaymentOperation claim(String key, long loanId, BigDecimal amount, LocalDate date);
    PaymentOperation succeeded(String key, long transactionId);
    PaymentOperation rejected(String key, String reason);
    PaymentOperation unknown(String key, String reason);
    PaymentOperation find(String key);
}
