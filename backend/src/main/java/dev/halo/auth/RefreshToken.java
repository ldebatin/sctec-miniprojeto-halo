package dev.halo.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Refresh token de sessão web (analise-tecnica §10.2) — tabela
 * {@code refresh_tokens} definida em V1__init.sql.
 *
 * O token em si nunca é persistido: apenas o hash bcrypt em
 * {@code tokenHash}. A revogação é por {@code revokedAt} (logout, rotação
 * a cada uso, expiração).
 *
 * {@code userAgent} e {@code ip} ficam registrados para auditoria — útil
 * em cenários de revogação por dispositivo no futuro.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 120)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Preenchido em logout/rotação; null = ainda válido. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
