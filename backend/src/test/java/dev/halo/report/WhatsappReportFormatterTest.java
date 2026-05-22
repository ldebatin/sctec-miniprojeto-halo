package dev.halo.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit do {@link WhatsappReportFormatter}: cabeçalho pt-BR, formato de
 * valor, alinhamento da tabela, mês vazio.
 */
class WhatsappReportFormatterTest {

    @Test
    void usa_nome_do_mes_em_pt_br_com_inicial_maiuscula() {
        assertThat(WhatsappReportFormatter.monthNamePtBr(LocalDate.of(2026, 5, 15)))
                .isEqualTo("Maio de 2026");
        assertThat(WhatsappReportFormatter.monthNamePtBr(LocalDate.of(2026, 1, 1)))
                .isEqualTo("Janeiro de 2026");
        assertThat(WhatsappReportFormatter.monthNamePtBr(LocalDate.of(2026, 12, 31)))
                .isEqualTo("Dezembro de 2026");
    }

    @Test
    void formata_valores_em_pt_br_com_2_casas() {
        assertThat(WhatsappReportFormatter.formatMoney(new BigDecimal("1234.5")))
                .isEqualTo("R$ 1.234,50");
        assertThat(WhatsappReportFormatter.formatMoney(new BigDecimal("0.10")))
                .isEqualTo("R$ 0,10");
        assertThat(WhatsappReportFormatter.formatMoney(new BigDecimal("9999.99")))
                .isEqualTo("R$ 9.999,99");
    }

    @Test
    void formata_porcentagem_como_inteiro_com_simbolo() {
        assertThat(WhatsappReportFormatter.formatPercent(new BigDecimal("80.00"))).isEqualTo("80%");
        assertThat(WhatsappReportFormatter.formatPercent(new BigDecimal("0.50"))).isEqualTo("1%");
        assertThat(WhatsappReportFormatter.formatPercent(new BigDecimal("100"))).isEqualTo("100%");
    }

    @Test
    void mes_vazio_devolve_mensagem_amigavel() {
        ReportDtos.MonthlyResponse vazio = monthly("2026-05", List.of(), "0");

        String out = WhatsappReportFormatter.format(vazio);

        assertThat(out).startsWith("📊 Resumo de Maio de 2026");
        assertThat(out).contains("Nada registrado neste mês.");
        assertThat(out).doesNotContain("```"); // sem tabela
    }

    @Test
    void mes_com_dados_monta_tabela_em_code_block() {
        ReportDtos.MonthlyResponse report = monthly(
                "2026-05",
                List.of(
                        cat("Mercado", "400.00", "80.00"),
                        cat("Lazer", "100.00", "20.00")),
                "500.00");

        String out = WhatsappReportFormatter.format(report);

        assertThat(out)
                .startsWith("📊 Resumo de Maio de 2026")
                .contains("```")
                .contains("Categoria")
                .contains("Valor")
                .contains("%")
                .contains("Mercado")
                .contains("R$ 400,00")
                .contains("80%")
                .contains("Lazer")
                .contains("R$ 100,00")
                .contains("20%")
                .contains("Total")
                .contains("R$ 500,00")
                .endsWith("```");
    }

    @Test
    void colunas_ficam_alinhadas_dentro_do_code_block() {
        ReportDtos.MonthlyResponse report = monthly(
                "2026-05",
                List.of(
                        cat("Mercado", "400.00", "80.00"),
                        cat("Lazer", "100.00", "20.00")),
                "500.00");

        String[] lines = WhatsappReportFormatter.format(report).split("\n");

        // procura o início do code block
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals("```")) { start = i; break; }
        }
        assertThat(start).isGreaterThanOrEqualTo(0);

        // todas as linhas dentro do bloco têm o mesmo comprimento (alinhamento)
        int referenceWidth = lines[start + 1].length();
        for (int i = start + 1; i < lines.length - 1; i++) {
            if (lines[i].equals("```")) break;
            assertThat(lines[i].length())
                    .as("linha %d: %s", i, lines[i])
                    .isEqualTo(referenceWidth);
        }
    }

    @Test
    void nome_de_categoria_longo_e_truncado_com_reticencias() {
        ReportDtos.MonthlyResponse report = monthly(
                "2026-05",
                List.of(cat("Categoria com nome muito muito longo", "10.00", "100")),
                "10.00");

        String out = WhatsappReportFormatter.format(report);
        // não pode ter o nome inteiro
        assertThat(out).doesNotContain("Categoria com nome muito muito longo");
        // tem o prefixo e o caractere de reticências
        assertThat(out).contains("Categoria c…");
    }

    @Test
    void output_cabe_em_uma_mensagem_whatsapp() {
        // gera uma resposta com 30 categorias (caso extremo)
        List<ReportDtos.Category> breakdown = new java.util.ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < 30; i++) {
            ReportDtos.Category c = cat("Cat" + i, "100.00", "3.33");
            breakdown.add(c);
            total = total.add(new BigDecimal("100.00"));
        }
        ReportDtos.MonthlyResponse big = monthly("2026-05", breakdown, total.toPlainString());

        String out = WhatsappReportFormatter.format(big);

        // limite seguro do WhatsApp é 4096 chars
        assertThat(out.length()).isLessThan(4000);
    }

    // ----------------------------------------------------------------

    private ReportDtos.MonthlyResponse monthly(
            String month, List<ReportDtos.Category> breakdown, String total) {
        return new ReportDtos.MonthlyResponse(
                month,
                LocalDate.parse(month + "-01"),
                LocalDate.parse(month + "-01").withDayOfMonth(28),
                new BigDecimal(total),
                breakdown,
                List.of());
    }

    private ReportDtos.Category cat(String name, String total, String pct) {
        return new ReportDtos.Category(
                UUID.randomUUID(), name, "#000000",
                new BigDecimal(total), new BigDecimal(pct));
    }
}
