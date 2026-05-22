package dev.halo.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gera imagem PNG do resumo mensal via QuickChart (RF-15, T-041,
 * analise-tecnica §9.3 — Opção A).
 *
 * Tipo de gráfico:
 * <ul>
 *   <li>{@code ≤ 6 categorias} → {@code pie} (visualização padrão).</li>
 *   <li>{@code > 6 categorias} → {@code bar} (legível com muitas barras).</li>
 * </ul>
 *
 * Resiliência: qualquer falha (timeout, 5xx, IO) devolve
 * {@link Optional#empty} — o caller usa o texto-only do
 * {@link WhatsappReportFormatter} como fallback (critério RF-15).
 *
 * Timeout duro de {@link ChartProperties#timeout()} (default 5s) tanto na
 * conexão quanto na leitura, via {@link HttpClient} do JDK — evita
 * interferir com a configuração global do {@code RestClient}.
 */
@Component
@Slf4j
public class QuickChartGenerator {

    static final int PIE_THRESHOLD = 6;
    private static final String CHART_PATH = "/chart";

    private final ChartProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QuickChartGenerator(ChartProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
    }

    /**
     * Gera o PNG do resumo. {@link Optional#empty} sinaliza fallback: o
     * caller deve enviar apenas a tabela de texto.
     */
    public Optional<byte[]> monthlyPie(ReportDtos.MonthlyResponse report) {
        if (report.breakdown().isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> payload = Map.of(
                "chart", buildConfig(report),
                "format", "png",
                "backgroundColor", "white");

        final String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Falha ao serializar config do gráfico: {}", e.getMessage());
            return Optional.empty();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + CHART_PATH))
                .timeout(properties.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<byte[]> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                log.warn("QuickChart status={} — fallback texto-only", response.statusCode());
                return Optional.empty();
            }
            byte[] png = response.body();
            if (png == null || png.length == 0) {
                log.warn("QuickChart devolveu corpo vazio");
                return Optional.empty();
            }
            return Optional.of(png);
        } catch (IOException e) {
            log.warn("Falha de IO ao chamar QuickChart — fallback texto-only: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupção ao chamar QuickChart — fallback texto-only");
            return Optional.empty();
        }
    }

    /**
     * Monta o config Chart.js. Package-private para teste direto sem
     * passar pela camada HTTP.
     */
    static Map<String, Object> buildConfig(ReportDtos.MonthlyResponse report) {
        List<ReportDtos.Category> breakdown = report.breakdown();
        boolean usePie = breakdown.size() <= PIE_THRESHOLD;

        List<String> labels = breakdown.stream().map(ReportDtos.Category::name).toList();
        List<BigDecimal> data = breakdown.stream().map(ReportDtos.Category::total).toList();
        List<String> colors = breakdown.stream().map(ReportDtos.Category::color).toList();

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("data", data);
        dataset.put("backgroundColor", colors);
        if (!usePie) {
            dataset.put("label", "Total");
        }

        Map<String, Object> chartData = new LinkedHashMap<>();
        chartData.put("labels", labels);
        chartData.put("datasets", List.of(dataset));

        Map<String, Object> title = new LinkedHashMap<>();
        title.put("display", true);
        title.put("text", "Resumo de " + WhatsappReportFormatter.monthNamePtBr(report.from()));

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("title", title);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", usePie ? "pie" : "bar");
        config.put("data", chartData);
        config.put("options", options);
        return config;
    }
}
