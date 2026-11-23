package com.collectionhub.infrastructure.fineract;

import com.collectionhub.domain.model.Installment;
import com.collectionhub.domain.port.out.LoanSchedulePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
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
public class FineractLoanScheduleAdapter implements LoanSchedulePort, com.collectionhub.domain.port.out.CollectionSchedulePort {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String tenant;
    private final String authorization;

    public FineractLoanScheduleAdapter(HttpClient fineractHttpClient, ObjectMapper mapper,
            @Value("${fineract.base-url}") String baseUrl,
            @Value("${fineract.tenant-id}") String tenant,
            @Value("${fineract.username}") String username,
            @Value("${fineract.password}") String password) {
        this.client = fineractHttpClient;
        this.mapper = mapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.tenant = tenant;
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public List<Installment> findInstallments(long loanId) {
        return mapSchedule(fetchLoan(loanId));
    }

    private JsonNode fetchLoan(long loanId) {
        String uri = baseUrl + "/loans/" + loanId + "?associations=repaymentSchedule";
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", authorization)
                .header("Fineract-Platform-TenantId", tenant)
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) throw new LoanNotFoundException(loanId);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FineractIntegrationException("Fineract respondió HTTP " + response.statusCode());
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FineractIntegrationException("Consulta a Fineract interrumpida", e);
        } catch (java.io.IOException e) {
            throw new FineractIntegrationException("No se pudo consultar Fineract", e);
        }
    }

    @Override
    public com.collectionhub.domain.model.LoanCollectionSnapshot fetch(long loanId) {
        JsonNode loan = fetchLoan(loanId);
        JsonNode periods = loan.path("repaymentSchedule").path("periods");
        if (!periods.isArray()) throw new FineractIntegrationException("Respuesta sin cronograma");
        String currency = loan.path("currency").path("code").asText("");
        if (currency.isBlank()) throw new FineractIntegrationException("Fineract no devolvió moneda");
        Long clientId = loan.path("clientId").isNumber() ? loan.path("clientId").asLong() : null;
        String clientName = loan.path("clientName").asText("");
        List<com.collectionhub.domain.model.CollectionOrder> orders = new ArrayList<>();
        for (JsonNode period : periods) {
            if (!period.hasNonNull("period") || period.path("period").asInt() <= 0) continue;
            JsonNode due = period.path("dueDate");
            if (!due.isArray() || due.size() != 3) throw new FineractIntegrationException("Vencimiento inválido");
            BigDecimal principal = requiredAmount(period, "principalOutstanding");
            BigDecimal interest = requiredAmount(period, "interestOutstanding");
            BigDecimal total = requiredAmount(period, "totalOutstandingForPeriod");
            if (total.compareTo(principal.add(interest)) != 0)
                throw new FineractIntegrationException("Componentes de deuda no concuerdan con total (cargos no soportados)");
            orders.add(new com.collectionhub.domain.model.CollectionOrder(null, null, loanId, clientId, clientName,
                    period.path("period").asInt(),
                    LocalDate.of(due.get(0).asInt(), due.get(1).asInt(), due.get(2).asInt()),
                    currency, principal, interest, total, "PENDING"));
        }
        return new com.collectionhub.domain.model.LoanCollectionSnapshot(loanId, clientId, clientName,
                currency, List.copyOf(orders));
    }

    private static BigDecimal requiredAmount(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) throw new FineractIntegrationException("Falta campo numérico: " + field);
        return value.decimalValue();
    }

    static List<Installment> mapSchedule(JsonNode loan) {
        JsonNode periods = loan.path("repaymentSchedule").path("periods");
        if (!periods.isArray()) throw new FineractIntegrationException("Respuesta sin repaymentSchedule.periods");
        List<Installment> result = new ArrayList<>();
        for (JsonNode p : periods) {
            // El período inicial de desembolso no tiene 'period'.
            if (!p.hasNonNull("period") || p.path("period").asInt() <= 0) continue;
            JsonNode date = p.path("dueDate");
            if (!date.isArray() || date.size() != 3) {
                throw new FineractIntegrationException("Fecha de vencimiento inválida");
            }
            result.add(new Installment(
                    p.path("period").asInt(),
                    LocalDate.of(date.get(0).asInt(), date.get(1).asInt(), date.get(2).asInt()),
                    amount(p, "principalDue"),
                    amount(p, "interestDue"),
                    amount(p, "totalDueForPeriod"),
                    amount(p, "totalPaidForPeriod"),
                    amount(p, "totalOutstandingForPeriod"),
                    p.path("complete").asBoolean(false)
            ));
        }
        return List.copyOf(result);
    }

    private static BigDecimal amount(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : BigDecimal.ZERO;
    }
}
