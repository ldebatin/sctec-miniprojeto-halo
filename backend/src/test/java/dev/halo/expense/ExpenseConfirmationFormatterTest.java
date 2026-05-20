package dev.halo.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Cobre o padrão textual pt-BR da T-015 (RF-04).
 */
class ExpenseConfirmationFormatterTest {

    @Test
    void formato_completo_pt_BR() {
        String msg = ExpenseConfirmationFormatter.format(
                "Mercado",
                new BigDecimal("87.30"),
                "Mercado",
                LocalDate.of(2026, 5, 18));

        assertThat(msg).isEqualTo(
                "Registrado: Mercado R$ 87,30 → Mercado. Data: 18/05. Use a web para alterar.");
    }

    @Test
    void valor_com_separador_de_milhar_pt_BR() {
        assertThat(ExpenseConfirmationFormatter.formatAmount(new BigDecimal("1234.56")))
                .isEqualTo("1.234,56");
        assertThat(ExpenseConfirmationFormatter.formatAmount(new BigDecimal("1234567.89")))
                .isEqualTo("1.234.567,89");
    }

    @Test
    void valor_inferior_a_mil_so_tem_vigula_decimal() {
        assertThat(ExpenseConfirmationFormatter.formatAmount(new BigDecimal("9.50")))
                .isEqualTo("9,50");
    }

    @Test
    void valor_sem_decimal_recebe_dois_zeros() {
        assertThat(ExpenseConfirmationFormatter.formatAmount(new BigDecimal("100")))
                .isEqualTo("100,00");
    }

    @Test
    void categoria_fallback_aparece_literal() {
        String msg = ExpenseConfirmationFormatter.format(
                "Petshop",
                new BigDecimal("45.00"),
                "Sem categoria",
                LocalDate.of(2026, 1, 7));

        assertThat(msg).contains("→ Sem categoria.");
        assertThat(msg).contains("Data: 07/01");
    }

    @Test
    void data_e_DD_MM_sem_ano() {
        String msg = ExpenseConfirmationFormatter.format(
                "Lanche",
                new BigDecimal("12.00"),
                "Alimentação",
                LocalDate.of(2026, 12, 31));

        assertThat(msg).contains("Data: 31/12.");
        assertThat(msg).doesNotContain("2026");
    }
}
