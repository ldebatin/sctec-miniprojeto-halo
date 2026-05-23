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
    void insere_nono_digito_em_celular_br_legado() {
        // JID antigo do WhatsApp chega sem o 9 — normalize precisa inserir
        assertThat(service.normalize("554799484436")).isEqualTo("+5547999484436");
    }

    @Test
    void insere_nono_digito_em_celular_br_legado_com_jid() {
        assertThat(service.normalize("554799484436@s.whatsapp.net"))
                .isEqualTo("+5547999484436");
    }

    @Test
    void insere_nono_digito_em_celular_br_legado_iniciando_com_8() {
        // Operadoras com prefixo 8 também são celular — precisam do 9 também
        assertThat(service.normalize("554788123456")).isEqualTo("+5547988123456");
    }

    @Test
    void mantem_numero_com_nono_digito_inalterado() {
        // Idempotência: chamar normalize duas vezes não acumula 9s
        String once = service.normalize("554799484436");
        assertThat(service.normalize(once)).isEqualTo(once);
    }

    @Test
    void nao_mexe_em_fixo_brasileiro() {
        // Fixos começam com 2-5; não devem receber o 9
        assertThat(service.normalize("554732123456")).isEqualTo("+554732123456");
    }

    @Test
    void nao_mexe_em_numero_fora_do_brasil() {
        // Outros DDIs não devem ser afetados, mesmo com 12 dígitos
        assertThat(service.normalize("351912345678")).isEqualTo("+351912345678");
    }
}
