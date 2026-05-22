package dev.halo.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit do {@link ClassificationCache} (T-043, RF-03 / analise-tecnica §9.4).
 */
class ClassificationCacheTest {

    private final ClassificationCache cache = new ClassificationCache();

    @Test
    void miss_devolve_optional_empty() {
        assertThat(cache.getHint(UUID.randomUUID(), "qualquer coisa")).isEmpty();
    }

    @Test
    void hit_devolve_hint_armazenado() {
        UUID userId = UUID.randomUUID();
        cache.putHint(userId, "Mercado 87,30", "Mercado");

        assertThat(cache.getHint(userId, "Mercado 87,30")).hasValue("Mercado");
    }

    @Test
    void chave_e_a_mesma_para_textos_com_acentos_e_caixas_diferentes() {
        UUID userId = UUID.randomUUID();
        cache.putHint(userId, "Almoço 30", "Alimentação");

        // mudou maiúscula, removeu acento, valor diferente — mesma chave
        assertThat(cache.getHint(userId, "ALMOCO 99,99")).hasValue("Alimentação");
        // pontuação e separadores também são ignorados
        assertThat(cache.getHint(userId, "almoço-15.50")).hasValue("Alimentação");
    }

    @Test
    void users_diferentes_nao_compartilham_classificacoes() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        cache.putHint(alice, "uber", "Transporte");
        cache.putHint(bob, "uber", "Lazer");

        assertThat(cache.getHint(alice, "uber")).hasValue("Transporte");
        assertThat(cache.getHint(bob, "uber")).hasValue("Lazer");
    }

    @Test
    void invalidate_remove_so_as_entradas_do_usuario_alvo() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        cache.putHint(alice, "uber", "Transporte");
        cache.putHint(alice, "mercado", "Mercado");
        cache.putHint(bob, "uber", "Transporte");

        cache.invalidate(alice);

        assertThat(cache.getHint(alice, "uber")).isEmpty();
        assertThat(cache.getHint(alice, "mercado")).isEmpty();
        assertThat(cache.getHint(bob, "uber")).hasValue("Transporte");
    }

    @Test
    void puthint_com_valor_null_ou_vazio_e_no_op() {
        UUID userId = UUID.randomUUID();
        cache.putHint(userId, "Mercado", null);
        cache.putHint(userId, "Mercado", "");
        cache.putHint(userId, "Mercado", "   ");

        assertThat(cache.getHint(userId, "Mercado")).isEmpty();
    }

    @Test
    void normalize_remove_digitos_pontuacao_e_acentos() {
        assertThat(ClassificationCache.normalize("Almoço 30,50"))
                .isEqualTo("almoco");
        assertThat(ClassificationCache.normalize("Uber: R$ 25.00 (motorista)"))
                .isEqualTo("uber r motorista");
        assertThat(ClassificationCache.normalize("    "))
                .isEqualTo("");
        assertThat(ClassificationCache.normalize(null))
                .isEqualTo("");
    }

    @Test
    void hash_e_estavel_e_de_32_chars() {
        String h1 = ClassificationCache.hashOf("mercado");
        String h2 = ClassificationCache.hashOf("mercado");
        assertThat(h1).hasSize(32).isEqualTo(h2);
    }

    @Test
    void stats_registra_hits_e_misses() {
        UUID userId = UUID.randomUUID();
        cache.putHint(userId, "mercado", "Mercado");

        cache.getHint(userId, "mercado");        // hit
        cache.getHint(userId, "outracoisa");     // miss

        assertThat(cache.stats().hitCount()).isEqualTo(1);
        assertThat(cache.stats().missCount()).isEqualTo(1);
    }
}
