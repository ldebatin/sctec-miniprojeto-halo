package dev.halo.whatsapp.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.halo.whatsapp.EvolutionClient;
import dev.halo.whatsapp.WhatsappMessage;
import dev.halo.whatsapp.WhatsappMessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cobre o fallback do RF-03/RF-04: quando o parser não reconhece a mensagem
 * como gasto, respondemos com a mensagem de ajuda em vez de ficar em silêncio.
 */
@ExtendWith(MockitoExtension.class)
class UnknownMessageHandlerTest {

    @Mock
    EvolutionClient evolutionClient;

    UnknownMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UnknownMessageHandler(evolutionClient);
    }

    @Test
    void envia_mensagem_de_ajuda_e_marca_NOT_UNDERSTOOD() {
        WhatsappMessage message = new WhatsappMessage();
        message.setEvolutionMsgId("M-42");
        message.setStatus(WhatsappMessageStatus.RECEIVED);
        assertThat(message.getProcessedAt()).isNull();

        handler.sendHelp("+5547999999999", message);

        verify(evolutionClient).sendText("+5547999999999", UnknownMessageHandler.HELP_MESSAGE);
        verifyNoMoreInteractions(evolutionClient);

        assertThat(message.getStatus()).isEqualTo(WhatsappMessageStatus.NOT_UNDERSTOOD);
        assertThat(message.getProcessedAt()).isNotNull();
    }

    @Test
    void mensagem_de_ajuda_contem_exemplos_dos_tres_fluxos() {
        // Smoke: se alguém apagar um dos blocos, este teste sinaliza.
        String text = UnknownMessageHandler.HELP_MESSAGE;

        assertThat(text).contains("Registrar um gasto");
        assertThat(text).contains("Mercado 87,30");
        assertThat(text).contains("Ver resumo do mês");
        assertThat(text).contains("resumo");
        assertThat(text).contains("Acessar a web");
        assertThat(text).contains("site");
    }
}
