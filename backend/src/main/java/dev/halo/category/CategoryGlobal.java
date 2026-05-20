package dev.halo.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Categoria global (template) visível a todos os usuários — RF-06,
 * analise-tecnica.md §6.1, tabela {@code categories_global}.
 *
 * Linhas são inseridas via Flyway (V3 seeda "Sem categoria"; o seed completo
 * com Alimentação, Mercado, etc. entra em T-033).
 */
@Entity
@Table(name = "categories_global")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class CategoryGlobal {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(nullable = false, length = 60)
    private String icon;

    @Column(nullable = false, length = 20)
    private String color;

    /** Sinônimos usados pelo classificador (T-013/T-043). */
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] keywords;
}
