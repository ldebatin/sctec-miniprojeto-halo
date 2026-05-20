package dev.halo.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Testes puros das funções utilitárias do {@link AiLogService} — SHA-256 do
 * prompt e estimativa de custo via {@link GeminiPricing}.
 */
class AiLogServiceUnitTest {

    @Test
    void sha256_string_vazia() {
        assertThat(AiLogService.sha256(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sha256_caso_referencia() {
        // Conhecido: SHA-256("abc") = ba7816bf8f01cfea4141...b00361a396177a9cb410ff61f20015ad
        assertThat(AiLogService.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256_nulo_e_tratado_como_string_vazia() {
        assertThat(AiLogService.sha256(null))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void custo_zero_para_chamada_sem_tokens() {
        assertThat(AiLogService.estimateCost(0, 0)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void custo_calculado_combinando_input_e_output() {
        // 1M input + 1M output = 0.075 + 0.30 = 0.375 USD
        BigDecimal cost = AiLogService.estimateCost(1_000_000, 1_000_000);
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.375000"));
    }

    @Test
    void custo_para_chamada_tipica_arredondado_para_6_casas() {
        // 1000 in + 50 out — valor pequeno, conferindo o arredondamento.
        BigDecimal cost = AiLogService.estimateCost(1000, 50);
        // 1000 * 0.075/1M + 50 * 0.30/1M = 0.000075 + 0.000015 = 0.000090
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.000090"));
    }

    @Test
    void custo_lida_com_tokens_negativos_como_zero() {
        assertThat(AiLogService.estimateCost(-5, 10))
                .isEqualByComparingTo(AiLogService.estimateCost(0, 10));
    }
}
