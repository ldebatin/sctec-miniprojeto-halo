package dev.halo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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

    // ----------------------------------------------------------------
    // rotate (T-021)
    // ----------------------------------------------------------------

    @Test
    void rotate_revoga_o_anterior_e_emite_um_novo() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RefreshTokenService service = new RefreshTokenService(repo);

        UUID userId = UUID.randomUUID();
        String plaintext = UUID.randomUUID().toString();
        RefreshToken existing = entityCom(userId, plaintext, Instant.now().plusSeconds(60), null);
        when(repo.findByTokenHash(RefreshTokenService.sha256Hex(plaintext)))
                .thenReturn(Optional.of(existing));

        RefreshTokenService.RotatedRefreshToken result =
                service.rotate(plaintext, "Mozilla/5.0", "10.0.0.1");

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.issued().token()).isNotBlank();
        assertThat(result.issued().token()).isNotEqualTo(plaintext);
        assertThat(existing.getRevokedAt()).isNotNull();
        verify(repo, times(2)).save(any()); // revoga o existente + salva o novo
    }

    @Test
    void rotate_lanca_para_token_ausente() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        RefreshTokenService service = new RefreshTokenService(repo);

        assertThatThrownBy(() -> service.rotate(null, "ua", "ip"))
                .isInstanceOf(RefreshTokenException.class);
        assertThatThrownBy(() -> service.rotate("", "ua", "ip"))
                .isInstanceOf(RefreshTokenException.class);

        verify(repo, never()).save(any());
    }

    @Test
    void rotate_lanca_para_token_desconhecido() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        when(repo.findByTokenHash(any())).thenReturn(Optional.empty());
        RefreshTokenService service = new RefreshTokenService(repo);

        assertThatThrownBy(() -> service.rotate("desconhecido", "ua", "ip"))
                .isInstanceOf(RefreshTokenException.class);

        verify(repo, never()).save(any());
    }

    @Test
    void rotate_lanca_para_token_ja_revogado() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        String plaintext = UUID.randomUUID().toString();
        RefreshToken existing = entityCom(UUID.randomUUID(), plaintext,
                Instant.now().plusSeconds(60), Instant.now().minusSeconds(1));
        when(repo.findByTokenHash(RefreshTokenService.sha256Hex(plaintext)))
                .thenReturn(Optional.of(existing));
        RefreshTokenService service = new RefreshTokenService(repo);

        assertThatThrownBy(() -> service.rotate(plaintext, "ua", "ip"))
                .isInstanceOf(RefreshTokenException.class);

        verify(repo, never()).save(any());
    }

    @Test
    void rotate_lanca_para_token_expirado() {
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        String plaintext = UUID.randomUUID().toString();
        RefreshToken existing = entityCom(UUID.randomUUID(), plaintext,
                Instant.now().minusSeconds(1), null);
        when(repo.findByTokenHash(RefreshTokenService.sha256Hex(plaintext)))
                .thenReturn(Optional.of(existing));
        RefreshTokenService service = new RefreshTokenService(repo);

        assertThatThrownBy(() -> service.rotate(plaintext, "ua", "ip"))
                .isInstanceOf(RefreshTokenException.class);

        verify(repo, never()).save(any());
    }

    private RefreshToken entityCom(UUID userId, String plaintext, Instant expiresAt, Instant revokedAt) {
        RefreshToken rt = new RefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setUserId(userId);
        rt.setTokenHash(RefreshTokenService.sha256Hex(plaintext));
        rt.setCreatedAt(Instant.now().minusSeconds(10));
        rt.setExpiresAt(expiresAt);
        rt.setRevokedAt(revokedAt);
        return rt;
    }
}
