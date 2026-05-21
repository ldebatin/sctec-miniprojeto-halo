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
 * Código OTP de 6 dígitos (RF-09) — tabela {@code otp_codes} definida em V1__init.sql.
 *
 * O código em si nunca é armazenado — apenas o hash bcrypt em {@code codeHash}.
 * TTL de 5 minutos controlado por {@code expiresAt}; {@code usedAt} não nulo
 * indica que o código já foi consumido.
 */
@Entity
@Table(name = "otp_codes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Telefone do destinatário em E.164. */
    @Column(nullable = false, length = 20)
    private String phone;

    /** Hash bcrypt do código de 6 dígitos. */
    @Column(name = "code_hash", nullable = false, length = 120)
    private String codeHash;

    /** Instante em que o código expira (TTL 5 min a partir da criação). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Preenchido quando o código é verificado com sucesso; null = ainda válido. */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Número de tentativas de verificação (máx. 5). */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
