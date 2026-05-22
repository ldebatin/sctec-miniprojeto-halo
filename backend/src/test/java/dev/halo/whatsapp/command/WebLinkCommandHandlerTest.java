package dev.halo.whatsapp.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.halo.web.WebProperties;
import dev.halo.whatsapp.EvolutionClient;
import dev.halo.whatsapp.WhatsappMessage;
import dev.halo.whatsapp.WhatsappMessageStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebLinkCommandHandlerTest {

    private static final String PUBLIC_URL = "https://halo.exemplo.app";

    private EvolutionClient evolutionClient;
    private WebLinkCommandHandler handler;

    @BeforeEach
    void setUp() {
        evolutionClient = mock(EvolutionClient.class);
        handler = new WebLinkCommandHandler(
                new WebProperties(PUBLIC_URL, List.of("site", "link", "web", "acessar")),
                evolutionClient
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"site", "LINK", "  Web  ", "ACESSAR"})
    void reconhece_gatilhos_case_insensitive(String text) {
        assertThat(handler.isLinkTrigger(text)).isTrue();
    }

    @Test
    void ignora_mensagem_que_nao_e_gatilho() {
        assertThat(handler.isLinkTrigger("Mercado 87,30")).isFalse();
        assertThat(handler.isLinkTrigger("meu site favorito")).isFalse();
    }

    @Test
    void envia_mensagem_com_url_clicavel_e_marca_processed() {
        WhatsappMessage message = new WhatsappMessage();
        message.setEvolutionMsgId("MSG-1");

        boolean handled = handler.tryHandle("+5547999999999", "site", message);

        assertThat(handled).isTrue();
        assertThat(message.getStatus()).isEqualTo(WhatsappMessageStatus.PROCESSED);
        assertThat(message.getProcessedAt()).isNotNull();
        verify(evolutionClient).sendText(
                eq("+5547999999999"),
                contains(PUBLIC_URL)
        );
    }

    @Test
    void nao_envia_quando_public_url_vazia() {
        WebLinkCommandHandler semUrl = new WebLinkCommandHandler(
                new WebProperties("", List.of("site")),
                evolutionClient
        );
        WhatsappMessage message = new WhatsappMessage();

        assertThat(semUrl.tryHandle("+5547999999999", "site", message)).isFalse();
        verify(evolutionClient, never()).sendText(eq("+5547999999999"), contains(PUBLIC_URL));
    }
}
