
package com.collectionhub.infrastructure.fineract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FineractPaymentAdapterTest {

    @Test
    void sendsExternalIdToFineract() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedPath = new AtomicReference<>();

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        server.createContext("/fineract-provider/api/v1/loans/1/transactions", exchange -> {
            try {
                receivedPath.set(
                        exchange.getRequestURI().toString()
                );

                receivedBody.set(
                        new String(
                                exchange.getRequestBody().readAllBytes(),
                                StandardCharsets.UTF_8
                        )
                );

                byte[] response =
                        "{\"resourceId\":999}".getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set(
                        "Content-Type", "application/json"
                );

                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });

        server.start();

        try {
            String baseUrl = "http://127.0.0.1:"
                    + server.getAddress().getPort()
                    + "/fineract-provider/api/v1";

            FineractPaymentAdapter adapter = new FineractPaymentAdapter(
                    HttpClient.newHttpClient(),
                    mapper,
                    baseUrl,
                    "default",
                    "mifos",
                    "password",
                    1
            );

            String externalId = "CH-test-payment-001";

            long transactionId = adapter.registerRepayment(
                    1L,
                    new BigDecimal("1066.19"),
                    LocalDate.of(2026, 11, 19),
                    externalId
            );

            assertEquals(999L, transactionId);

            assertEquals(
                    "/fineract-provider/api/v1/loans/1/transactions?command=repayment",
                    receivedPath.get()
            );

            JsonNode body = mapper.readTree(receivedBody.get());

            assertEquals(
                    externalId,
                    body.path("externalId").asText()
            );

            assertEquals(
                    0,
                    new BigDecimal("1066.19").compareTo(
                            body.path("transactionAmount").decimalValue()
                    )
            );

            assertEquals(
                    "19 November 2026",
                    body.path("transactionDate").asText()
            );

            assertEquals(
                    1,
                    body.path("paymentTypeId").asInt()
            );
        } finally {
            server.stop(0);
        }
    }
}
