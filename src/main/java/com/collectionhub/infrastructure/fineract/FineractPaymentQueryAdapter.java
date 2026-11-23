
package com.collectionhub.infrastructure.fineract;

import com.collectionhub.domain.model.FineractPaymentTransaction;
import com.collectionhub.domain.port.out.PaymentQueryPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FineractPaymentQueryAdapter implements PaymentQueryPort {

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String tenant;
    private final String authorization;

    public FineractPaymentQueryAdapter(
            HttpClient fineractHttpClient,
            ObjectMapper mapper,
            @Value("${fineract.base-url}") String baseUrl,
            @Value("${fineract.tenant-id}") String tenant,
            @Value("${fineract.username}") String username,
            @Value("${fineract.password}") String password) {

        this.client = fineractHttpClient;
        this.mapper = mapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.tenant = tenant;
        this.authorization = "Basic "
                + Base64.getEncoder().encodeToString(
                (username + ":" + password)
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public List<FineractPaymentTransaction> findTransactionsByLoanId(
            long loanId) {

        if (loanId <= 0) {
            throw new IllegalArgumentException(
                    "loanId debe ser mayor que cero"
            );
        }

        try {
            List<FineractPaymentTransaction> result =
                    new ArrayList<>();

            int page = 0;
            final int pageSize = 50;

            while (true) {
                String url = baseUrl + "/loans/" + loanId
                        + "/transactions?page=" + page
                        + "&size=" + pageSize;

                HttpRequest request = HttpRequest.newBuilder(
                                URI.create(url))
                        .timeout(Duration.ofSeconds(25))
                        .header("Authorization", authorization)
                        .header("Fineract-Platform-TenantId", tenant)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() != 200) {
                    throw new FineractIntegrationException(
                            "Error consultando transacciones: HTTP "
                                    + response.statusCode()
                    );
                }

                JsonNode body = mapper.readTree(response.body());
                JsonNode content = body.path("content");

                if (!content.isArray()) {
                    throw new FineractIntegrationException(
                            "Respuesta inválida de transacciones Fineract"
                    );
                }

                for (JsonNode item : content) {
                    if (!item.path("type")
                            .path("repayment")
                            .asBoolean(false)) {
                        continue;
                    }

                    result.add(new FineractPaymentTransaction(
                            item.path("id").asLong(),
                            item.path("loanId").asLong(),
                            item.path("amount").decimalValue(),
                            LocalDate.parse(
                                    item.path("date").asText()
                            ),
                            item.path("manuallyReversed")
                                    .asBoolean(false),
                            item.path("externalId").asText(null)
                    ));
                }

                if (!body.has("last")
                        || !body.path("last").isBoolean()) {
                    throw new FineractIntegrationException(
                            "Respuesta sin indicador de paginación válido"
                    );
                }

                if (body.path("last").asBoolean()) {
                    break;
                }

                page++;
            }

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FineractIntegrationException(
                    "Consulta de transacciones interrumpida", e
            );
        } catch (java.io.IOException e) {
            throw new FineractIntegrationException(
                    "Error de comunicación con Fineract", e
            );
        }
    }
}
