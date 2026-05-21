package dev.halo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do {@link JwtService}: claims emitidas, TTL e validação
 * de segredo curto (< 256 bits).
 */
class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("00000000000000000000000000000000".getBytes()); // 32 bytes

    @Test
    void issue_emite_jwt_com_sub_phone_iss_exp() {
        JwtService service = new JwtService(
                new JwtProperties(SECRET, Duration.ofMinutes(15), "halo"));
        UUID userId = UUID.randomUUID();

        JwtService.IssuedAccessToken issued = service.issueAccessToken(userId, "+5547999999999");

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresIn()).isEqualTo(Duration.ofMinutes(15));

        Claims claims = parseClaims(issued.token());
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("phone", String.class)).isEqualTo("+5547999999999");
        assertThat(claims.getIssuer()).isEqualTo("halo");
        long expSeconds = claims.getExpiration().toInstant().getEpochSecond();
        long iatSeconds = claims.getIssuedAt().toInstant().getEpochSecond();
        assertThat(expSeconds - iatSeconds).isEqualTo(15 * 60);
        assertThat(claims.getExpiration().toInstant()).isAfter(Instant.now());
    }

    @Test
    void construtor_rejeita_segredo_com_menos_de_256_bits() {
        String shortSecret = Base64.getEncoder()
                .encodeToString("curto".getBytes()); // 5 bytes
        assertThatThrownBy(() ->
                new JwtService(new JwtProperties(shortSecret, Duration.ofMinutes(15), "halo")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private Claims parseClaims(String token) {
        byte[] keyBytes = Base64.getDecoder().decode(SECRET);
        return Jwts.parser()
                .verifyWith(new SecretKeySpec(keyBytes, "HmacSHA256"))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
