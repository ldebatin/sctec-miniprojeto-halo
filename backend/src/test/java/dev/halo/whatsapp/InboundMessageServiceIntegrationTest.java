package dev.halo.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração T-009 com Testcontainers + Postgres 16: cobre os critérios de
 * aceitação da task — idempotência ({@code evolution_msg_id} UNIQUE), telefone
 * normalizado em E.164 e {@code direction='IN'} em mensagens recebidas.
 */
@SpringBootTest
@Testcontainers
class InboundMessageServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private InboundMessageService service;

    @Autowired
    private WhatsappMessageRepository repository;

    private static EvolutionPayloadDto payload(String msgId, String jid, String text) {
        return new EvolutionPayloadDto(
                "messages.upsert",
                "halo-bot",
                new EvolutionPayloadDto.Data(
                        new EvolutionPayloadDto.Key(msgId, jid, false),
                        new EvolutionPayloadDto.Message(text),
                        1_716_042_000L,
                        "Maria"
                )
        );
    }

    @Test
    void mesma_msgId_recebida_2x_cria_apenas_1_linha() {
        EvolutionPayloadDto p = payload("DUP-1", "5547999999999@s.whatsapp.net", "Mercado 87,30");

        WhatsappMessage first = service.record(p);
        WhatsappMessage second = service.record(p);

        assertThat(repository.count()).isEqualTo(1L);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void persiste_com_direction_IN_e_status_RECEIVED() {
        EvolutionPayloadDto p = payload("IN-1", "5547888888888@s.whatsapp.net", "Uber 25");

        WhatsappMessage saved = service.record(p);

        WhatsappMessage reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDirection()).isEqualTo(WhatsappDirection.IN);
        assertThat(reloaded.getStatus()).isEqualTo(WhatsappMessageStatus.RECEIVED);
        assertThat(reloaded.getEvolutionMsgId()).isEqualTo("IN-1");
        assertThat(reloaded.getContent()).isEqualTo("Uber 25");
        assertThat(reloaded.getReceivedAt()).isNotNull();
        assertThat(reloaded.getProcessedAt()).isNull();
        // userId fica nulo em T-009 — T-010 fará o lookup pelo phone normalizado.
        assertThat(reloaded.getUserId()).isNull();
    }

    @Test
    void normaliza_jid_para_E164() {
        assertThat(InboundMessageService.normalizeJidToE164("5547777777777@s.whatsapp.net"))
                .isEqualTo("+5547777777777");
    }
}
