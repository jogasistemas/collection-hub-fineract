package com.collectionhub.infrastructure.fineract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FineractLoanScheduleAdapterTest {
    @Test
    void skipsDisbursementAndMapsInstallments() throws Exception {
        String json = """
            {"repaymentSchedule":{"periods":[
              {"dueDate":[2026,11,19],"principalDisbursed":12000},
              {"period":1,"dueDate":[2026,12,19],"principalDue":946.19,
               "interestDue":120.00,"totalDueForPeriod":1066.19,
               "totalPaidForPeriod":0,"totalOutstandingForPeriod":1066.19,"complete":false}
            ]}}
            """;
        var installments = FineractLoanScheduleAdapter.mapSchedule(new ObjectMapper().readTree(json));
        assertEquals(1, installments.size());
        assertEquals(1, installments.getFirst().installmentNumber());
        assertEquals("2026-12-19", installments.getFirst().dueDate().toString());
        assertEquals(0, installments.getFirst().totalDue().compareTo(new java.math.BigDecimal("1066.19")));
    }
    @Test
    void mapsPaidFirstInstallment() throws Exception {
        String json = """
            {"repaymentSchedule":{"periods":[
              {"dueDate":[2026,11,19],"principalDisbursed":12000},
              {"period":1,"dueDate":[2026,12,19],"principalDue":946.19,
               "interestDue":120,"totalDueForPeriod":1066.19,
               "totalPaidForPeriod":1066.19,"totalOutstandingForPeriod":0,"complete":true}
            ]}}
            """;
        var first = FineractLoanScheduleAdapter.mapSchedule(new ObjectMapper().readTree(json)).getFirst();
        assertEquals(0, first.totalPaid().compareTo(new java.math.BigDecimal("1066.19")));
        assertEquals(0, first.outstandingAmount().signum());
        assertTrue(first.paid());
    }
}
