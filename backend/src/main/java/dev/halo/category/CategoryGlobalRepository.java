package dev.halo.category;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryGlobalRepository extends JpaRepository<CategoryGlobal, UUID> {

    /** Match case-insensitive usado pelo resolver de categoria (T-014). */
    Optional<CategoryGlobal> findByNameIgnoreCase(String name);
}
