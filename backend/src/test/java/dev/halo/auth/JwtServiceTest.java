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

    // ----------------------------------------------------------------
    // parseAccessToken (T-021)
    // ----------------------------------------------------------------

    @Test
    void parse_devolve_userId_e_phone_para_token_valido() {
        JwtService service = new JwtService(
                new JwtProperties(SECRET, Duration.ofMinutes(15), "halo"));
        UUID userId = UUID.randomUUID();
        String token = service.issueAccessToken(userId, "+5547999999999").token();

        JwtService.ParsedAccessToken parsed = service.parseAccessToken(token);

        assertThat(parsed.userId()).isEqualTo(userId);
        assertThat(parsed.phone()).isEqualTo("+5547999999999");
    }

    @Test
    void parse_falha_para_assinatura_invalida() {
        JwtService issuer = new JwtService(
                new JwtProperties(SECRET, Duration.ofMinutes(15), "halo"));
        String token = issuer.issueAccessToken(UUID.randomUUID(), "+5547999999999").token();

        String otherSecret = Base64.getEncoder()
                .encodeToString("11111111111111111111111111111111".getBytes());
        JwtService outroVerificador = new JwtService(
                new JwtProperties(otherSecret, Duration.ofMinutes(15), "halo"));

        assertThatThrownBy(() -> outroVerificador.parseAccessToken(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void parse_falha_para_token_expirado() {
        JwtService service = new JwtService(
                new JwtProperties(SECRET, Duration.ofSeconds(-1), "halo"));
        String token = service.issueAccessToken(UUID.randomUUID(), "+5547999999999").token();

        assertThatThrownBy(() -> service.parseAccessToken(token))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void parse_falha_para_token_de_outro_issuer() {
        JwtService outro = new JwtService(
                new JwtProperties(SECRET, Duration.ofMinutes(15), "outro-emissor"));
        String token = outro.issueAccessToken(UUID.randomUUID(), "+5547999999999").token();

        JwtService verificador = new JwtService(
                new JwtProperties(SECRET, Duration.ofMinutes(15), "halo"));

        assertThatThrownBy(() -> verificador.parseAccessToken(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
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
