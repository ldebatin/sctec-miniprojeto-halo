package dev.halo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhoneNumberServiceTest {

    private final PhoneNumberService service = new PhoneNumberService();

    @Test
    void normaliza_numero_ja_com_mais() {
        assertThat(service.normalize("+5547999999999")).isEqualTo("+5547999999999");
    }

    @Test
    void normaliza_numero_sem_mais() {
        assertThat(service.normalize("5547999999999")).isEqualTo("+5547999999999");
    }

    @Test
    void normaliza_jid_com_sufixo_whatsapp() {
        assertThat(service.normalize("5547999999999@s.whatsapp.net"))
                .isEqualTo("+5547999999999");
    }

    @Test
    void remove_device_id_do_jid() {
        assertThat(service.normalize("5547999999999:1@s.whatsapp.net"))
                .isEqualTo("+5547999999999");
    }

    @Test
    void remove_decoracao_comum_de_digitacao() {
        assertThat(service.normalize("+55 (47) 99999-9999")).isEqualTo("+5547999999999");
    }

    @Test
    void rejeita_entrada_nula() {
        assertThatThrownBy(() -> service.normalize(null))
                .isInstanceOf(InvalidPhoneException.class);
    }

    @Test
    void rejeita_entrada_em_branco() {
        assertThatThrownBy(() -> service.normalize("   "))
                .isInstanceOf(InvalidPhoneException.class);
    }

    @Test
    void rejeita_numero_curto_demais() {
        assertThatThrownBy(() -> service.normalize("1234567"))
                .isInstanceOf(InvalidPhoneException.class);
    }

    @Test
    void rejeita_numero_longo_demais() {
        assertThatThrownBy(() -> service.normalize("1234567890123456"))
                .isInstanceOf(InvalidPhoneException.class);
    }

    @Test
    void canonicaliza_movel_brasileiro_legado_para_formato_moderno() {
        // JID legado do Evolution Go vem sem o 9 após o DDD; deve virar o
        // mesmo telefone que o usuário digita no formulário web (13 dígitos).
        assertThat(service.normalize("554799484436@s.whatsapp.net"))
                .isEqualTo("+5547999484436");
        assertThat(service.normalize("554799484436")).isEqualTo("+5547999484436");
    }

    @Test
    void mantem_movel_brasileiro_ja_no_formato_moderno() {
        assertThat(service.normalize("+5547999484436")).isEqualTo("+5547999484436");
        assertThat(service.normalize("5547999484436@s.whatsapp.net"))
                .isEqualTo("+5547999484436");
    }

    @Test
    void nao_altera_fixo_brasileiro_de_12_digitos() {
        // Fixo começa com 2–5; não deve ganhar o 9 móvel.
        assertThat(service.normalize("554732261000")).isEqualTo("+554732261000");
    }

    @Test
    void nao_altera_numero_nao_brasileiro_de_12_digitos() {
        assertThat(service.normalize("123456789012")).isEqualTo("+123456789012");
    }
}
