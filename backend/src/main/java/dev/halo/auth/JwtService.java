package dev.halo.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Emissão de access tokens JWT HMAC-SHA256 (analise-tecnica §10.2).
 *
 * Claims:
 * <ul>
 *   <li>{@code sub} — {@code userId} (UUID).</li>
 *   <li>{@code phone} — telefone E.164 (usado em logs e UX, evita um JOIN
 *       para descobrir o telefone do usuário em cada request).</li>
 *   <li>{@code iss} / {@code iat} / {@code exp} — emissão e expiração.</li>
 * </ul>
 *
 * Validação dos tokens (filtro do Spring Security) entra em T-021 — esta
 * task só emite.
 */
@Service
@Slf4j
public class JwtService {

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        byte[] decoded = Base64.getDecoder().decode(properties.secret());
        if (decoded.length < 32) {
            throw new IllegalStateException(
                    "halo.auth.jwt.secret precisa ter pelo menos 32 bytes após decodificação Base64");
        }
        this.signingKey = new SecretKeySpec(decoded, "HmacSHA256");
        this.accessTokenTtl = properties.accessTokenTtl();
        this.issuer = properties.issuer();
    }

    /** Emite um access token para o usuário identificado por {@code userId}. */
    public IssuedAccessToken issueAccessToken(UUID userId, String phone) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);

        String jwt = Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("phone", phone)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedAccessToken(jwt, accessTokenTtl);
    }

    /**
     * Valida assinatura e expiração de um access token e devolve as claims
     * relevantes. Usado pelo {@code JwtAuthenticationFilter} em T-021.
     *
     * @throws JwtException se a assinatura é inválida, o token está expirado,
     *     ou as claims obrigatórias estão ausentes/mal formadas.
     */
    public ParsedAccessToken parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            UUID userId = UUID.fromString(claims.getSubject());
            String phone = claims.get("phone", String.class);
            if (phone == null || phone.isBlank()) {
                throw new JwtException("claim 'phone' ausente");
            }
            return new ParsedAccessToken(userId, phone);
        } catch (IllegalArgumentException e) {
            throw new JwtException("claim 'sub' inválida: não é UUID", e);
        }
    }

    public record IssuedAccessToken(String token, Duration expiresIn) {}

    public record ParsedAccessToken(UUID userId, String phone) {}
}
