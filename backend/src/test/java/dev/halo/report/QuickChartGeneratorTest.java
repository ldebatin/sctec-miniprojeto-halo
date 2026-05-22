package dev.halo.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit do {@link QuickChartGenerator}.
 *
 * Cobre:
 * <ul>
 *   <li>Geração do config Chart.js (pie ≤ 6, bar > 6, cores propagadas).</li>
 *   <li>Sucesso/erro HTTP via {@link HttpServer} do JDK (sem dependência externa).</li>
 *   <li>Fallback ({@link Optional#empty}) em 5xx, timeout, corpo vazio e
 *       breakdown vazio.</li>
 * </ul>
 */
class QuickChartGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    // ----------------------------------------------------------------
    // buildConfig
    // ----------------------------------------------------------------

    @Test
    void buildConfig_usa_pie_para_ate_6_categorias() {
        Map<String, Object> config = QuickChartGenerator.buildConfig(
                monthlyComBreakdownDeTamanho(6));
        assertThat(config.get("type")).isEqualTo("pie");
    }

    @Test
    void buildConfig_usa_bar_para_mais_de_6_categorias() {
        Map<String, Object> config = QuickChartGenerator.buildConfig(
                monthlyComBreakdownDeTamanho(7));
        assertThat(config.get("type")).isEqualTo("bar");
    }

    @Test
    void buildConfig_propaga_cores_e_labels_em_ordem() throws Exception {
        ReportDtos.MonthlyResponse r = monthly(
                List.of(
                        cat("Mercado", "400", "80", "#0000FF"),
                        cat("Lazer", "100", "20", "#FF0000")));

        Map<String, Object> config = QuickChartGenerator.buildConfig(r);
        JsonNode json = objectMapper.valueToTree(config);

        assertThat(json.get("type").asText()).isEqualTo("pie");
        JsonNode labels = json.get("data").get("labels");
        assertThat(labels.get(0).asText()).isEqualTo("Mercado");
        assertThat(labels.get(1).asText()).isEqualTo("Lazer");
        JsonNode dataset = json.get("data").get("datasets").get(0);
        assertThat(dataset.get("data").get(0).decimalValue()).isEqualByComparingTo("400");
        assertThat(dataset.get("backgroundColor").get(0).asText()).isEqualTo("#0000FF");
        assertThat(dataset.get("backgroundColor").get(1).asText()).isEqualTo("#FF0000");
        assertThat(json.get("options").get("title").get("text").asText())
                .isEqualTo("Resumo de Maio de 2026");
    }

    @Test
    void buildConfig_em_bar_inclui_label_no_dataset() {
        Map<String, Object> config = QuickChartGenerator.buildConfig(
                monthlyComBreakdownDeTamanho(7));
        JsonNode json = objectMapper.valueToTree(config);
        assertThat(json.get("data").get("datasets").get(0).get("label").asText())
                .isEqualTo("Total");
    }

    // ----------------------------------------------------------------
    // monthlyPie — HTTP feliz e falhas
    // ----------------------------------------------------------------

    @Test
    void breakdown_vazio_devolve_empty_sem_chamar_o_server() {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/chart", ex -> { hits.incrementAndGet(); ex.sendResponseHeaders(200, 0); ex.close(); });

        QuickChartGenerator gen = generatorWith(Duration.ofSeconds(5));
        Optional<byte[]> result = gen.monthlyPie(monthly(List.of()));

        assertThat(result).isEmpty();
        assertThat(hits.get()).isZero();
    }

    @Test
    void server_2xx_devolve_bytes() throws Exception {
        byte[] fakePng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        AtomicReference<byte[]> capturedBody = new AtomicReference<>();
        server.createContext("/chart", ex -> {
            capturedBody.set(ex.getRequestBody().readAllBytes());
            ex.getResponseHeaders().set("Content-Type", "image/png");
            ex.sendResponseHeaders(200, fakePng.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(fakePng);
            }
        });

        QuickChartGenerator gen = generatorWith(Duration.ofSeconds(5));
        Optional<byte[]> result = gen.monthlyPie(monthly(
                List.of(cat("Mercado", "400", "100", "#0000FF"))));

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(fakePng);

        // body enviado contém o config Chart.js
        String sent = new String(capturedBody.get());
        assertThat(sent).contains("\"type\":\"pie\"");
        assertThat(sent).contains("Mercado");
    }

    @Test
    void server_5xx_devolve_empty() {
        server.createContext("/chart", ex -> {
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });
        QuickChartGenerator gen = generatorWith(Duration.ofSeconds(2));

        Optional<byte[]> result = gen.monthlyPie(monthly(
                List.of(cat("Mercado", "400", "100", "#0000FF"))));

        assertThat(result).isEmpty();
    }

    @Test
    void server_4xx_devolve_empty() {
        server.createContext("/chart", ex -> {
            ex.sendResponseHeaders(400, -1);
            ex.close();
        });
        QuickChartGenerator gen = generatorWith(Duration.ofSeconds(2));

        Optional<byte[]> result = gen.monthlyPie(monthly(
                List.of(cat("Mercado", "400", "100", "#0000FF"))));

        assertThat(result).isEmpty();
    }

    @Test
    void server_corpo_vazio_devolve_empty() {
        server.createContext("/chart", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        QuickChartGenerator gen = generatorWith(Duration.ofSeconds(2));

        Optional<byte[]> result = gen.monthlyPie(monthly(
                List.of(cat("Mercado", "400", "100", "#0000FF"))));

        assertThat(result).isEmpty();
    }

    @Test
    void timeout_estourado_devolve_empty() {
        server.createContext("/chart", ex -> {
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        QuickChartGenerator gen = generatorWith(Duration.ofMillis(200));

        Optional<byte[]> result = gen.monthlyPie(monthly(
                List.of(cat("Mercado", "400", "100", "#0000FF"))));

        assertThat(result).isEmpty();
    }

    // ----------------------------------------------------------------

    private QuickChartGenerator generatorWith(Duration timeout) {
        return new QuickChartGenerator(new ChartProperties(baseUrl, timeout), objectMapper);
    }

    private ReportDtos.MonthlyResponse monthlyComBreakdownDeTamanho(int n) {
        List<ReportDtos.Category> bd = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            bd.add(cat("Cat" + i, "100", "0", "#000000"));
        }
        return monthly(bd);
    }

    private ReportDtos.MonthlyResponse monthly(List<ReportDtos.Category> breakdown) {
        BigDecimal total = breakdown.stream()
                .map(ReportDtos.Category::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReportDtos.MonthlyResponse(
                "2026-05",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                total,
                breakdown,
                List.of());
    }

    private ReportDtos.Category cat(String name, String total, String pct, String color) {
        return new ReportDtos.Category(
                UUID.randomUUID(), name, color,
                new BigDecimal(total), new BigDecimal(pct));
    }
}
