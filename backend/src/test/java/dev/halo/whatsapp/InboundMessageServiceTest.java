package dev.halo.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Testes unitários da normalização inline JID → E.164.
 *
 * A versão completa (com validação rigorosa e exceções tratadas) entra em
 * {@code PhoneNumberService} na T-010.
 */
class InboundMessageServiceTest {

    @Test
    void normaliza_jid_com_sufixo_whatsapp() {
        assertThat(InboundMessageService.normalizeJidToE164("5547999999999@s.whatsapp.net"))
                .isEqualTo("+5547999999999");
    }

    @Test
    void normaliza_numero_sem_mais() {
        assertThat(InboundMessageService.normalizeJidToE164("5547999999999"))
                .isEqualTo("+5547999999999");
    }

    @Test
    void preserva_numero_ja_com_mais() {
        assertThat(InboundMessageService.normalizeJidToE164("+5547999999999"))
                .isEqualTo("+5547999999999");
    }

    @Test
    void devolve_null_para_entrada_nula_ou_em_branco() {
        assertThat(InboundMessageService.normalizeJidToE164(null)).isNull();
        assertThat(InboundMessageService.normalizeJidToE164("")).isNull();
        assertThat(InboundMessageService.normalizeJidToE164("   ")).isNull();
    }
}
