package dev.halo.whatsapp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Linha de log de uma mensagem trocada com o WhatsApp via Evolution Go
 * (analise-tecnica.md §6.2 — tabela {@code whatsapp_messages}).
 *
 * {@code evolution_msg_id} é UNIQUE no banco — é a chave de idempotência usada
 * por {@link InboundMessageService} para evitar reprocessar redeliveries do
 * Evolution (RF-01).
 *
 * O campo {@code userId} fica nulo até T-010 (lookup por telefone E.164).
 *
 * Esquema é dono do Flyway ({@code spring.jpa.hibernate.ddl-auto=validate}),
 * então qualquer divergência aqui quebra o boot — fonte da verdade é
 * {@code V1__init.sql}.
 */
@Entity
@Table(name = "whatsapp_messages")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class WhatsappMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** FK opcional para {@code users.id}; T-009 sempre grava nulo (T-010 popula). */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "evolution_msg_id", nullable = false, unique = true, length = 120)
    private String evolutionMsgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private WhatsappDirection direction;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WhatsappMessageStatus status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
