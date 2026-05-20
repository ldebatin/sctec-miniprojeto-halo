package dev.halo.user;

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
 * Conta única de usuário no Halo (analise-tecnica.md §6.1 — tabela {@code users}).
 *
 * Identificada pelo telefone em E.164 (UNIQUE no banco). O cadastro
 * conversacional ({@code AWAITING_NAME} → criar usuário) entra na T-011 —
 * aqui só temos o modelo + lookup por telefone necessários para a T-010.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Em E.164 (sempre normalizado via {@link PhoneNumberService}). */
    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
