package dev.halo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Testes unitários do {@link RefreshTokenService}: emissão de UUID v4 opaco,
 * persistência com hash SHA-256, e captura de metadados (user-agent, ip).
 */
class RefreshTokenServiceTest {

    @Test
    void issue_persiste_hash_sha256_do_token_plaintext() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RefreshTokenService service = new RefreshTokenService(repo);
        UUID userId = UUID.randomUUID();

        RefreshTokenService.IssuedRefreshToken issued =
                service.issue(userId, "Mozilla/5.0", "127.0.0.1");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTokenHash()).hasSize(64);
        assertThat(saved.getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.sha256Hex(issued.token()));
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getIp()).isEqualTo("127.0.0.1");
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void issue_define_ttl_de_30_dias() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RefreshTokenService service = new RefreshTokenService(repo);
        Instant before = Instant.now();

        RefreshTokenService.IssuedRefreshToken issued =
                service.issue(UUID.randomUUID(), "ua", "1.2.3.4");

        assertThat(issued.ttl()).isEqualTo(Duration.ofDays(30));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        Instant expected = before.plus(Duration.ofDays(30));
        assertThat(captor.getValue().getExpiresAt()).isAfterOrEqualTo(expected.minusSeconds(5));
        assertThat(captor.getValue().getExpiresAt()).isBeforeOrEqualTo(expected.plusSeconds(5));
    }

    @Test
    void issue_trunca_user_agent_e_ip_em_caso_de_excesso() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RefreshTokenService service = new RefreshTokenService(repo);

        String longUa = "a".repeat(300);
        String longIp = "0".repeat(60);
        service.issue(UUID.randomUUID(), longUa, longIp);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getUserAgent()).hasSize(255);
        assertThat(captor.getValue().getIp()).hasSize(45);
    }

    @Test
    void issue_aceita_user_agent_e_ip_nulos() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RefreshTokenService service = new RefreshTokenService(repo);

        service.issue(UUID.randomUUID(), null, null);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getUserAgent()).isNull();
        assertThat(captor.getValue().getIp()).isNull();
    }
}
