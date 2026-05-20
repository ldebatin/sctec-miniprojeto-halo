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
}
