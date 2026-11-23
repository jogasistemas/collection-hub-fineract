
package com.collectionhub.infrastructure.fineract;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FineractPaymentQueryAdapterTest {

    @Test
    void shouldReturnOnlyRepaymentTransactions() throws Exception {

        HttpClient client = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);

        String json = """
            {
              "content": [
                {
                  "id": 1,
                  "loanId": 1,
                  "type": {"repayment": false},
                  "date": "2026-09-15",
                  "amount": 12000,
                  "manuallyReversed": false
                },
                {
                   "id": 3,
                   "loanId": 1,
                   "type": {"repayment": true},
                   "date": "2026-10-15",
                   "amount": 500,
                   "externalId": "CH-payment-00001",
                   "manuallyReversed": false
                 },
                {
                   "id": 4,
                   "loanId": 1,
                   "type": {"repayment": true},
                   "date": "2026-10-15",
                   "amount": 620,
                   "externalId": null,
                   "manuallyReversed": true
                 }
              ],
              "last": true
            }
            """;

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(json);

        when(client.send(
                any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(response);

        var adapter = new FineractPaymentQueryAdapter(
                client,
                new ObjectMapper(),
                "https://localhost:8444/fineract-provider/api/v1",
                "default",
                "test-user",
                "test-password"
        );

        var transactions =
                adapter.findTransactionsByLoanId(1);

        assertEquals(2, transactions.size());

        var first = transactions.get(0);

        assertEquals(3L, first.transactionId());
        assertEquals(1L, first.loanId());
        assertEquals(
                0,
                new BigDecimal("500.00")
                        .compareTo(first.amount())
        );
        assertEquals(
                LocalDate.of(2026, 10, 15),
                first.transactionDate()
        );
        assertFalse(first.reversed());
        assertEquals("CH-payment-00001", first.externalId());

        var second = transactions.get(1);

        assertEquals(4L, second.transactionId());
        assertTrue(second.reversed());
        assertNull(second.externalId());

        verify(client, times(1))
                .send(any(), any());
    }


    @Test
    void shouldRetrieveTransactionsFromMultiplePages() throws Exception {

        HttpClient client = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> firstResponse = mock(HttpResponse.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> secondResponse = mock(HttpResponse.class);

        String firstPage = """
        {
          "content": [
            {
              "id": 10,
              "loanId": 1,
              "type": {"repayment": true},
              "date": "2026-10-15",
              "amount": 500,
              "manuallyReversed": false
            }
          ],
          "last": false
        }
        """;

        String secondPage = """
        {
          "content": [
            {
              "id": 11,
              "loanId": 1,
              "type": {"repayment": true},
              "date": "2026-11-15",
              "amount": 620,
              "externalId": "CH-payment-00002",
              "manuallyReversed": false
            }
          ],
          "last": true
        }
        """;

        when(firstResponse.statusCode()).thenReturn(200);
        when(firstResponse.body()).thenReturn(firstPage);

        when(secondResponse.statusCode()).thenReturn(200);
        when(secondResponse.body()).thenReturn(secondPage);

        when(client.send(
                any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(firstResponse, secondResponse);

        var adapter = new FineractPaymentQueryAdapter(
                client,
                new ObjectMapper(),
                "https://localhost:8444/fineract-provider/api/v1",
                "default",
                "test-user",
                "test-password"
        );

        var transactions = adapter.findTransactionsByLoanId(1);

        assertEquals(2, transactions.size());
        assertEquals(10L, transactions.get(0).transactionId());
        assertEquals(11L, transactions.get(1).transactionId());

        assertNull(transactions.get(0).externalId());

        assertEquals(
                "CH-payment-00002",
                transactions.get(1).externalId()
        );

        verify(client, times(2)).send(
                any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        );
    }

}
