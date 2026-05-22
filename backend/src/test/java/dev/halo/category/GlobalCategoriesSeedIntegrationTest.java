package dev.halo.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Valida o estado das categorias globais semeadas pelas migrations
 * V3 (Sem categoria) + V4 (12 globais) + V5 (refinamento de keywords e
 * ícones para lucide-react) — RF-06, T-033.
 *
 * Sobe o contexto Spring com Postgres real para que todas as 5 migrations
 * sejam aplicadas em ordem.
 */
@SpringBootTest
@Testcontainers
class GlobalCategoriesSeedIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    /** Nomes esperados (12 globais + "Sem categoria"). */
    private static final List<String> EXPECTED_NAMES = List.of(
            "Sem categoria",
            "Alimentação", "Mercado", "Transporte", "Lazer", "Saúde",
            "Moradia", "Educação", "Vestuário", "Serviços",
            "Investimento", "Renda", "Outros");

    /** Ícones esperados — todos válidos em {@code lucide-react}. */
    private static final Map<String, String> EXPECTED_ICONS = Map.ofEntries(
            Map.entry("Sem categoria", "circle-help"),
            Map.entry("Alimentação", "utensils"),
            Map.entry("Mercado", "shopping-cart"),
            Map.entry("Transporte", "car"),
            Map.entry("Lazer", "sparkles"),
            Map.entry("Saúde", "heart"),
            Map.entry("Moradia", "house"),
            Map.entry("Educação", "graduation-cap"),
            Map.entry("Vestuário", "shirt"),
            Map.entry("Serviços", "wrench"),
            Map.entry("Investimento", "trending-up"),
            Map.entry("Renda", "banknote"),
            Map.entry("Outros", "ellipsis"));

    @Autowired
    private CategoryGlobalRepository repository;

    @Test
    void todas_as_13_categorias_estao_semeadas() {
        List<String> names = repository.findAll().stream()
                .map(CategoryGlobal::getName)
                .sorted()
                .toList();

        assertThat(names).containsExactlyInAnyOrderElementsOf(EXPECTED_NAMES);
    }

    @Test
    void icones_sao_nomes_lucide_react_validos() {
        Map<String, String> actualIcons = repository.findAll().stream()
                .collect(Collectors.toMap(CategoryGlobal::getName, CategoryGlobal::getIcon));

        for (Map.Entry<String, String> expected : EXPECTED_ICONS.entrySet()) {
            assertThat(actualIcons.get(expected.getKey()))
                    .as("ícone de %s", expected.getKey())
                    .isEqualTo(expected.getValue());
        }
    }

    @Test
    void cores_estao_no_formato_hex() {
        for (CategoryGlobal c : repository.findAll()) {
            assertThat(c.getColor())
                    .as("cor de %s", c.getName())
                    .matches("#[0-9A-Fa-f]{6}");
        }
    }

    @Test
    void categorias_principais_tem_keywords_populadas() {
        Map<String, String[]> keywords = repository.findAll().stream()
                .collect(Collectors.toMap(CategoryGlobal::getName, CategoryGlobal::getKeywords));

        // 11 categorias principais devem ter ≥ 5 keywords cada
        List<String> nomesComKeywords = List.of(
                "Alimentação", "Mercado", "Transporte", "Lazer", "Saúde",
                "Moradia", "Educação", "Vestuário", "Serviços",
                "Investimento", "Renda");
        for (String name : nomesComKeywords) {
            assertThat(keywords.get(name))
                    .as("keywords de %s", name)
                    .isNotNull()
                    .hasSizeGreaterThanOrEqualTo(5);
        }
    }

    @Test
    void keywords_de_mercado_contem_supermercado() {
        CategoryGlobal mercado = repository.findByNameIgnoreCase("Mercado").orElseThrow();
        assertThat(Arrays.asList(mercado.getKeywords()))
                .contains("supermercado")
                .contains("atacadão");
    }

    @Test
    void categorias_catch_all_tem_keywords_vazias() {
        // "Outros" e "Sem categoria" são catch-alls explícitos: sinônimos
        // colidiriam com as outras 11 categorias.
        CategoryGlobal outros = repository.findByNameIgnoreCase("Outros").orElseThrow();
        CategoryGlobal semCategoria = repository.findByNameIgnoreCase("Sem categoria").orElseThrow();
        assertThat(outros.getKeywords()).isEmpty();
        assertThat(semCategoria.getKeywords()).isEmpty();
    }
}
