package dev.halo.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do {@link OtpRateLimiter}.
 *
 * Verifica o cooldown de 60s por telefone via Bucket4j em memória.
 */
class OtpRateLimiterTest {

    private OtpRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new OtpRateLimiter();
    }

    @Test
    void primeira_tentativa_e_permitida() {
        assertThat(rateLimiter.tryConsume("+5547999999999")).isTrue();
    }

    @Test
    void segunda_tentativa_imediata_e_bloqueada() {
        rateLimiter.tryConsume("+5547999999999");

        assertThat(rateLimiter.tryConsume("+5547999999999")).isFalse();
    }

    @Test
    void telefones_diferentes_tem_buckets_independentes() {
        assertThat(rateLimiter.tryConsume("+5547999999991")).isTrue();
        assertThat(rateLimiter.tryConsume("+5547999999992")).isTrue();
    }

    @Test
    void segundo_telefone_nao_e_afetado_pelo_cooldown_do_primeiro() {
        rateLimiter.tryConsume("+5547999999991");
        rateLimiter.tryConsume("+5547999999991"); // esgota o bucket do primeiro

        // segundo telefone ainda tem token disponível
        assertThat(rateLimiter.tryConsume("+5547999999992")).isTrue();
    }
}
