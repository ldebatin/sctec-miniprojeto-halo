package dev.halo.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório JPA para {@link RefreshToken}.
 *
 * Validação por hash + rotação na rota {@code POST /auth/refresh} entram
 * em T-021.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
