package dev.halo.whatsapp.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.halo.report.QuickChartGenerator;
import dev.halo.report.ReportDtos;
import dev.halo.report.ReportService;
import dev.halo.user.User;
import dev.halo.whatsapp.EvolutionClient;
import dev.halo.whatsapp.WhatsappMessage;
import dev.halo.whatsapp.WhatsappMessageStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit do {@link SummaryCommandHandler}: parser + ReportService mockados,
 * verificando os caminhos feliz, vazio e com falha de gráfico.
 */
class SummaryCommandHandlerTest {

    private ReportService reportService;
    private QuickChartGenerator chartGenerator;
    private EvolutionClient evolutionClient;
    private SummaryCommandHandler handler;
    private User user;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        chartGenerator = mock(QuickChartGenerator.class);
        evolutionClient = mock(EvolutionClient.class);
        handler = new SummaryCommandHandler(reportService, chartGenerator, evolutionClient);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setPhone("+5547999999999");
        user.setName("Carla");
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
    }

    @Test
    void mensagem_que_nao_e_resumo_devolve_false_e_nao_chama_servicos() {
        boolean handled = handler.tryHandle(user, "+5547999999999",
                "mercado 50", new WhatsappMessage());

        assertThat(handled).isFalse();
        verify(reportService, never()).monthly(any(), any());
        verify(evolutionClient, never()).sendText(any(), any());
        verify(evolutionClient, never()).sendMedia(any(), any(), any(), any());
    }

    @Test
    void mes_vazio_envia_apenas_mensagem_amigavel_sem_chart() {
        ReportDtos.MonthlyResponse vazio = monthlyVazio("2026-05");
        when(reportService.monthly(eq(user.getId()), any())).thenReturn(vazio);

        WhatsappMessage msg = new WhatsappMessage();
        boolean handled = handler.tryHandle(user, "+5547999999999", "resumo", msg);

        assertThat(handled).isTrue();
        verify(evolutionClient).sendText(eq("+5547999999999"),
                contains("Nada registrado em Maio de 2026"));
        verify(chartGenerator, never()).monthlyPie(any());
        verify(evolutionClient, never()).sendMedia(any(), any(), any(), any());
        assertThat(msg.getStatus()).isEqualTo(WhatsappMessageStatus.PROCESSED);
        assertThat(msg.getProcessedAt()).isNotNull();
    }

    @Test
    void mes_com_dados_envia_tabela_e_media_quando_chart_disponivel() {
        ReportDtos.MonthlyResponse report = monthlyComDados("2026-05");
        when(reportService.monthly(eq(user.getId()), any())).thenReturn(report);
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        when(chartGenerator.monthlyPie(report)).thenReturn(Optional.of(png));

        WhatsappMessage msg = new WhatsappMessage();
        boolean handled = handler.tryHandle(user, "+5547999999999", "resumo abril", msg);

        assertThat(handled).isTrue();
        verify(evolutionClient).sendText(eq("+5547999999999"), contains("Mercado"));
        verify(evolutionClient).sendMedia(
                eq("+5547999999999"), eq(png), eq("image/png"), eq(null));
        assertThat(msg.getStatus()).isEqualTo(WhatsappMessageStatus.PROCESSED);
    }

    @Test
    void chart_indisponivel_ainda_envia_texto_e_sucede() {
        ReportDtos.MonthlyResponse report = monthlyComDados("2026-05");
        when(reportService.monthly(eq(user.getId()), any())).thenReturn(report);
        when(chartGenerator.monthlyPie(report)).thenReturn(Optional.empty());

        WhatsappMessage msg = new WhatsappMessage();
        boolean handled = handler.tryHandle(user, "+5547999999999", "resumo", msg);

        assertThat(handled).isTrue();
        verify(evolutionClient).sendText(any(), any());
        verify(evolutionClient, never()).sendMedia(any(), any(), any(), any());
        assertThat(msg.getStatus()).isEqualTo(WhatsappMessageStatus.PROCESSED);
    }

    @Test
    void falha_no_sendMedia_nao_anula_a_tabela_e_segue_marcando_processed() {
        ReportDtos.MonthlyResponse report = monthlyComDados("2026-05");
        when(reportService.monthly(eq(user.getId()), any())).thenReturn(report);
        when(chartGenerator.monthlyPie(report))
                .thenReturn(Optional.of(new byte[]{1, 2, 3}));
        org.mockito.Mockito.doThrow(new RuntimeException("evolution offline"))
                .when(evolutionClient).sendMedia(any(), any(), any(), any());

        WhatsappMessage msg = new WhatsappMessage();
        boolean handled = handler.tryHandle(user, "+5547999999999", "resumo", msg);

        assertThat(handled).isTrue();
        verify(evolutionClient, times(1)).sendText(any(), any());
        assertThat(msg.getStatus()).isEqualTo(WhatsappMessageStatus.PROCESSED);
    }

    @Test
    void parser_resolve_yearmonth_alvo_correto() {
        ReportDtos.MonthlyResponse report = monthlyComDados("2025-04");
        org.mockito.ArgumentCaptor<YearMonth> captor =
                org.mockito.ArgumentCaptor.forClass(YearMonth.class);
        when(reportService.monthly(eq(user.getId()), captor.capture())).thenReturn(report);
        when(chartGenerator.monthlyPie(any())).thenReturn(Optional.empty());

        handler.tryHandle(user, "+5547999999999",
                "resumo de abril 2025", new WhatsappMessage());

        assertThat(captor.getValue()).isEqualTo(YearMonth.of(2025, 4));
    }

    // ----------------------------------------------------------------

    private ReportDtos.MonthlyResponse monthlyVazio(String month) {
        return new ReportDtos.MonthlyResponse(
                month,
                LocalDate.parse(month + "-01"),
                LocalDate.parse(month + "-01").withDayOfMonth(28),
                BigDecimal.ZERO,
                List.of(),
                List.of());
    }

    private ReportDtos.MonthlyResponse monthlyComDados(String month) {
        ReportDtos.Category cat = new ReportDtos.Category(
                UUID.randomUUID(), "Mercado", "#0000FF",
                new BigDecimal("400.00"), new BigDecimal("100.00"));
        return new ReportDtos.MonthlyResponse(
                month,
                LocalDate.parse(month + "-01"),
                LocalDate.parse(month + "-01").withDayOfMonth(28),
                new BigDecimal("400.00"),
                List.of(cat),
                List.of());
    }
}
