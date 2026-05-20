package dev.halo.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Cobre a heurística do fallback (T-016) para os formatos mais comuns que o
 * usuário brasileiro escreve.
 */
class AmountExtractorTest {

    @Test
    void extrai_decimal_pt_BR_com_virgula() {
        assertThat(AmountExtractor.extract("Mercado 87,30"))
                .isEqualByComparingTo(new BigDecimal("87.30"));
    }

    @Test
    void extrai_inteiro_simples() {
        assertThat(AmountExtractor.extract("Uber 25"))
                .isEqualByComparingTo(new BigDecimal("25"));
    }

    @Test
    void extrai_decimal_en_com_ponto() {
        assertThat(AmountExtractor.extract("Mercado 87.30"))
                .isEqualByComparingTo(new BigDecimal("87.30"));
    }

    @Test
    void extrai_valor_pt_BR_com_milhar_e_decimal() {
        assertThat(AmountExtractor.extract("Notebook 1.234,56"))
                .isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    void interpreta_ponto_como_milhar_quando_seguido_de_3_digitos() {
        assertThat(AmountExtractor.extract("Aluguel 1.500"))
                .isEqualByComparingTo(new BigDecimal("1500"));
    }

    @Test
    void ignora_prefixo_R_dollar() {
        assertThat(AmountExtractor.extract("Café R$5,50"))
                .isEqualByComparingTo(new BigDecimal("5.50"));
    }

    @Test
    void primeira_ocorrencia_vence_em_texto_com_varios_numeros() {
        assertThat(AmountExtractor.extract("12 pacotes Mercado 87,30"))
                .isEqualByComparingTo(new BigDecimal("12"));
    }

    @Test
    void texto_sem_numero_devolve_null() {
        assertThat(AmountExtractor.extract("Oi, tudo bem?")).isNull();
    }

    @Test
    void texto_nulo_ou_vazio_devolve_null() {
        assertThat(AmountExtractor.extract(null)).isNull();
        assertThat(AmountExtractor.extract("")).isNull();
        assertThat(AmountExtractor.extract("   ")).isNull();
    }
}
