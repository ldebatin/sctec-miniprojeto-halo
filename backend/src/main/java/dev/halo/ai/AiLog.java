package dev.halo.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Auditoria de chamadas ao Gemini — tabela {@code ai_log} (analise-tecnica.md
 * §3.2/§9.4).
 *
 * Não armazenamos o prompt completo, só o {@code promptHash} (SHA-256) — é
 * suficiente pra detectar repetições no cache (T-043) e protege privacidade
 * (CLAUDE.md §Conventions — logs nunca contêm conteúdo de mensagem).
 */
@Entity
@Table(name = "ai_log")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class AiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(name = "prompt_hash", nullable = false, length = 64)
    private String promptHash;

    @Column(name = "tokens_in", nullable = false)
    private int tokensIn;

    @Column(name = "tokens_out", nullable = false)
    private int tokensOut;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiLogStatus status;

    /** numeric(10,6) — custo estimado em USD da chamada. */
    @Column(name = "cost_est", nullable = false, precision = 10, scale = 6)
    private BigDecimal costEst;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
