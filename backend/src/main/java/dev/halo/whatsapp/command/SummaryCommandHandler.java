package dev.halo.whatsapp.command;

import dev.halo.report.QuickChartGenerator;
import dev.halo.report.ReportDtos;
import dev.halo.report.ReportService;
import dev.halo.report.WhatsappReportFormatter;
import dev.halo.user.User;
import dev.halo.whatsapp.EvolutionClient;
import dev.halo.whatsapp.WhatsappMessage;
import dev.halo.whatsapp.WhatsappMessageStatus;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler do comando "resumo" no fluxo do WhatsApp (RF-15, RF-16 — T-038).
 *
 * Encadeia parser → {@link ReportService} → {@link WhatsappReportFormatter}
 * (texto) → {@link QuickChartGenerator} (PNG opcional, com fallback) →
 * {@link EvolutionClient}. Marca a mensagem como {@code PROCESSED} ao
 * final para que o pipeline de gasto não rode.
 *
 * Estratégia de envio em duas mensagens (sempre):
 * <ol>
 *   <li>Texto formatado (tabela em monoespaçado) via {@code sendText}.</li>
 *   <li>PNG do gráfico via {@code sendMedia}, caption vazia. Pulado quando
 *       o {@link QuickChartGenerator} devolve {@link Optional#empty}
 *       (fallback texto-only — RF-15).</li>
 * </ol>
 *
 * Mês sem dados → uma única mensagem "Nada registrado em &lt;mês&gt;" e
 * não tenta o gráfico (critério T-038 + RF-16).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SummaryCommandHandler {

    static final String EMPTY_MESSAGE_TEMPLATE = "Nada registrado em %s.";

    private final ReportService reportService;
    private final QuickChartGenerator chartGenerator;
    private final EvolutionClient evolutionClient;

    /**
     * Trata a mensagem se for um comando de resumo do usuário {@code user}.
     *
     * @return {@code true} se o comando foi reconhecido e a resposta enviada
     */
    public boolean tryHandle(User user, String phoneE164, String content, WhatsappMessage message) {
        SummaryCommandParser.Match match = SummaryCommandParser.match(content);
        if (!match.found()) {
            return false;
        }
        YearMonth target = match.targetMonth() != null ? match.targetMonth() : YearMonth.now();

        ReportDtos.MonthlyResponse report = reportService.monthly(user.getId(), target);

        if (report.breakdown().isEmpty()) {
            String empty = EMPTY_MESSAGE_TEMPLATE.formatted(
                    WhatsappReportFormatter.monthNamePtBr(report.from()));
            evolutionClient.sendText(phoneE164, empty);
            markProcessed(message);
            log.info("Resumo vazio enviado userId={} month={}", user.getId(), target);
            return true;
        }

        String tableText = WhatsappReportFormatter.format(report);
        evolutionClient.sendText(phoneE164, tableText);

        Optional<byte[]> chart = chartGenerator.monthlyPie(report);
        if (chart.isPresent()) {
            try {
                evolutionClient.sendMedia(phoneE164, chart.get(), "image/png", null);
            } catch (RuntimeException e) {
                // Falha do sendMedia não anula o resumo — a tabela em texto
                // já chegou. Apenas logamos para diagnóstico.
                log.warn("Falha ao enviar gráfico do resumo userId={} month={} erro={}",
                        user.getId(), target, e.getMessage());
            }
        }

        markProcessed(message);
        log.info("Resumo enviado userId={} month={} categorias={}",
                user.getId(), target, report.breakdown().size());
        return true;
    }

    private static void markProcessed(WhatsappMessage message) {
        message.setStatus(WhatsappMessageStatus.PROCESSED);
        message.setProcessedAt(Instant.now());
    }
}
