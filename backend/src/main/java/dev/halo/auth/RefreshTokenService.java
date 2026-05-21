package dev.halo.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emissão e persistência de refresh tokens (analise-tecnica §10.2).
 *
 * Token plaintext é um UUID v4 opaco (128 bits de entropia) — alto o
 * suficiente para tornar a verificação por bcrypt desnecessária. Aqui
 * usamos **SHA-256** porque a coluna {@code token_hash} é {@code UNIQUE}
 * e a verificação em T-021 precisa de lookup por hash exato (bcrypt
 * teria salt aleatório por linha).
 *
 * Rotação (revoga anterior + emite novo) entra na rota {@code /auth/refresh}
 * da T-021. Esta task só faz a emissão inicial em {@code /auth/otp/verify}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final RefreshTokenRepository repository;

    /**
     * Rotaciona um refresh token (T-021): valida o plaintext recebido no
     * cookie, revoga o token anterior e emite um novo no lugar.
     *
     * @throws RefreshTokenException se o token está ausente, é desconhecido,
     *     já foi revogado ou está expirado.
     */
    @Transactional
    public RotatedRefreshToken rotate(String plaintext, String userAgent, String ip) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new RefreshTokenException("refresh token ausente");
        }

        String hash = sha256Hex(plaintext);
        RefreshToken current = repository.findByTokenHash(hash)
                .orElseThrow(() -> new RefreshTokenException("refresh token desconhecido"));

        Instant now = Instant.now();
        if (current.getRevokedAt() != null) {
            throw new RefreshTokenException("refresh token já revogado");
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw new RefreshTokenException("refresh token expirado");
        }

        current.setRevokedAt(now);
        repository.save(current);

        IssuedRefreshToken next = issue(current.getUserId(), userAgent, ip);
        return new RotatedRefreshToken(current.getUserId(), next);
    }

    /**
     * Revoga o refresh token associado ao plaintext recebido no cookie
     * (T-022, RF-11). Idempotente: tokens ausentes, desconhecidos, já
     * revogados ou expirados são tratados como no-op — logout sempre
     * sucede do ponto de vista do cliente.
     */
    @Transactional
    public void revoke(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return;
        }
        repository.findByTokenHash(sha256Hex(plaintext)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                repository.save(token);
            }
        });
    }

    /**
     * Emite um novo refresh token, persiste o hash + metadados e devolve
     * o plaintext (para o cookie httpOnly).
     */
    public IssuedRefreshToken issue(UUID userId, String userAgent, String ip) {
        String plaintext = UUID.randomUUID().toString();
        String hash = sha256Hex(plaintext);

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plus(REFRESH_TOKEN_TTL));
        entity.setUserAgent(truncate(userAgent, 255));
        entity.setIp(truncate(ip, 45));

        repository.save(entity);

        return new IssuedRefreshToken(plaintext, REFRESH_TOKEN_TTL);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record IssuedRefreshToken(String token, Duration ttl) {}

    public record RotatedRefreshToken(UUID userId, IssuedRefreshToken issued) {}
}
