package dev.halo.category;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** Match case-insensitive entre categorias ativas do usuário (T-014). */
    Optional<Category> findByUserIdAndActiveTrueAndNameIgnoreCase(UUID userId, String name);

    /** Reutiliza a cópia local quando o usuário já lançou algo numa global (T-014). */
    Optional<Category> findByUserIdAndGlobalIdAndActiveTrue(UUID userId, UUID globalId);
}
