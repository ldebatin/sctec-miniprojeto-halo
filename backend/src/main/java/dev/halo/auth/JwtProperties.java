package dev.halo.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de assinatura/emissão do access token JWT (analise-tecnica §10.2).
 *
 * <ul>
 *   <li>{@code secret} — chave HMAC-SHA256 em Base64. Mínimo 256 bits (32 bytes
 *       decodificados) — exigência da {@code jjwt}.</li>
 *   <li>{@code accessTokenTtl} — TTL do access token, default 15 min.</li>
 *   <li>{@code issuer} — claim {@code iss} do JWT.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "halo.auth.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        String issuer
) {}
