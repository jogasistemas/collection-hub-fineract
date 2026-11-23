package com.collectionhub.application;

import com.collectionhub.domain.model.Installment;
import com.collectionhub.domain.port.out.InstallmentRepositoryPort;
import com.collectionhub.domain.port.out.LoanSchedulePort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SyncLoanInstallmentsUseCaseTest {
    @Test
    void syncSavesFineractInstallments() {
        LoanSchedulePort fineract = mock(LoanSchedulePort.class);
        InstallmentRepositoryPort repository = mock(InstallmentRepositoryPort.class);
        Installment installment = new Installment(1, LocalDate.of(2026, 12, 19),
                new BigDecimal("946.19"), new BigDecimal("120.00"),
                new BigDecimal("1066.19"), BigDecimal.ZERO, new BigDecimal("1066.19"), false);
        when(fineract.findInstallments(1L)).thenReturn(List.of(installment));
        List<Installment> result = new SyncLoanInstallmentsUseCase(fineract, repository).execute(1L);
        assertEquals(1, result.size());
        verify(repository).replaceForLoan(1L, List.of(installment));
    }
}
