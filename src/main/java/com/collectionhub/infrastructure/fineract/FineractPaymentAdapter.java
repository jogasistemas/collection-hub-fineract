package com.collectionhub.infrastructure.fineract;

import com.collectionhub.domain.port.out.PaymentGatewayPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FineractPaymentAdapter implements PaymentGatewayPort {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String tenant;
    private final String authorization;
    private final int paymentTypeId;

    public FineractPaymentAdapter(HttpClient fineractHttpClient, ObjectMapper mapper,
            @Value("${fineract.base-url}") String baseUrl,
            @Value("${fineract.tenant-id}") String tenant,
            @Value("${fineract.username}") String username,
            @Value("${fineract.password}") String password,
            @Value("${fineract.payment-type-id}") int paymentTypeId) {
        this.client = fineractHttpClient;
        this.mapper = mapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.tenant = tenant;
        this.authorization = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.paymentTypeId = paymentTypeId;
        System.out.println("========== FINERACT CONFIG ==========");
        System.out.println("Base URL: " + this.baseUrl);
        System.out.println("Tenant: " + this.tenant);
        System.out.println("Payment Type ID: " + this.paymentTypeId);
        System.out.println("=====================================");
    }

    @Override
    public long registerRepayment(
            long loanId,
            BigDecimal amount,
            LocalDate paymentDate,
            String externalId) {
        try {
            String json = mapper.writeValueAsString(Map.of(
                    "locale", "en",
                    "dateFormat", "dd MMMM yyyy",
                    "transactionDate", paymentDate.format(FORMAT),
                    "transactionAmount", amount,
                    "paymentTypeId", paymentTypeId,
                    "externalId", externalId));
            String repaymentUrl = baseUrl + "/loans/" + loanId
                    + "/transactions?command=repayment";

            System.out.println("FINERACT REPAYMENT URL: " + repaymentUrl);

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(repaymentUrl))
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", authorization)
                    .header("Fineract-Platform-TenantId", tenant)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 && response.statusCode() < 500) {
                throw new FineractPaymentRejectedException("Fineract rechazó el pago (HTTP " + response.statusCode() + "): " + response.body().substring(0, Math.min(200, response.body().length())));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new FineractIntegrationException("Respuesta incierta de Fineract: HTTP " + response.statusCode());
            JsonNode body = mapper.readTree(response.body());
            JsonNode id = body.path("resourceId");
            if (!id.canConvertToLong() || id.asLong() <= 0)
                throw new FineractIntegrationException("Fineract respondió sin resourceId válido; conciliar pago");
            return id.asLong();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FineractIntegrationException("Envío de pago interrumpido; resultado incierto", e);
        } catch (java.io.IOException e) {
            throw new FineractIntegrationException("No se pudo confirmar el pago en Fineract", e);
        }
    }
}
