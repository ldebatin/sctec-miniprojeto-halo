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
}
