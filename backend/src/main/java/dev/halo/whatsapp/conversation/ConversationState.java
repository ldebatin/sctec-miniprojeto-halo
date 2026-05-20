package dev.halo.whatsapp.conversation;

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
 * Estado conversacional 1:1 por telefone (analise-tecnica.md §6.2 — tabela
 * {@code conversation_state}, schema redefinido na V2).
 *
 * O estado é gravado por telefone antes do usuário existir (fluxo de cadastro
 * §7.1 / T-011); {@code userId} fica nulo durante {@code AWAITING_NAME} e é
 * preenchido se a tabela vier a ser usada para fluxos pós-cadastro.
 */
@Entity
@Table(name = "conversation_state")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ConversationState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversationStatus state;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
