package dev.halo.report;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do gerador de gráfico via QuickChart (RF-15, T-041,
 * analise-tecnica §9.3 — Opção A).
 *
 * <ul>
 *   <li>{@code baseUrl} — endpoint da QuickChart. Default público; em testes
 *       pode apontar para um mock.</li>
 *   <li>{@code timeout} — máximo de espera por resposta. Default 5s (RF-15).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "halo.report.quickchart")
public record ChartProperties(
        String baseUrl,
        Duration timeout
) {}
