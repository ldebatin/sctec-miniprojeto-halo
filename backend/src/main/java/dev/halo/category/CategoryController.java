package dev.halo.category;

import dev.halo.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD HTTP de categorias (RF-07, RF-08).
 *
 * Cobertura por task:
 * <ul>
 *   <li>T-034 entregou {@code GET/POST/PATCH/DELETE /categories}.</li>
 *   <li>T-035 (esta task) adiciona {@code POST /categories/from-global/{id}}
 *       (RF-08 — cópia/override de global), esconde do {@code GET} as globais
 *       que o user já personalizou, e permite {@code PATCH/DELETE} em cópias
 *       de globais (até T-034 esse caminho devolvia 403 sem necessidade —
 *       o objetivo do RF-08 é exatamente permitir essa edição).</li>
 * </ul>
 *
 * <h2>Decisão para lançamentos passados (RF-08, analise-tecnica §17)</h2>
 *
 * Quando o usuário cria a cópia de uma global, **os lançamentos antigos
 * continuam ligados à categoria que existia no momento do lançamento**.
 * Como o {@code ExpenseService.resolveCategoryForUser} já cria uma cópia
 * lazy do global para o user na primeira utilização (T-014), os lançamentos
 * passados costumam estar apontados para a *própria* cópia lazy que vira o
 * destino do {@code POST /from-global}: ou seja, customizações feitas via
 * {@code PATCH /categories/{id}} se refletem no histórico naturalmente.
 *
 * O endpoint {@code from-global} é, portanto, **idempotente**: se a cópia
 * já existe (lazy ou explícita), devolve 200 com a existente; só cria nova
 * em 201 quando não há nenhuma para o par {@code (userId, globalId)}.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final CategoryGlobalRepository categoryGlobalRepository;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories(@AuthenticationPrincipal User user) {
        List<Category> customs = categoryRepository.findByUserIdAndActiveTrue(user.getId());
        // Globais que o user já personalizou — escondidas da lista de globais
        // (RF-08): apenas a cópia customizada aparece.
        Set<UUID> overriddenGlobalIds = customs.stream()
                .map(Category::getGlobalId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        List<CategoryResponse> all = new ArrayList<>();
        for (CategoryGlobal g : categoryGlobalRepository.findAll()) {
            if (!overriddenGlobalIds.contains(g.getId())) {
                all.add(new CategoryResponse(g));
            }
        }
        for (Category c : customs) {
            all.add(new CategoryResponse(c));
        }
        return ResponseEntity.ok(all);
    }

    @PostMapping
    public ResponseEntity<?> createCategory(
            @AuthenticationPrincipal User user, @RequestBody CategoryRequest req) {
        if (req.name == null || req.name.trim().isEmpty() || req.name.trim().length() > 50) {
            return ResponseEntity.badRequest().body("Nome deve ter entre 1 e 50 caracteres");
        }
        if (req.icon == null || req.icon.trim().isEmpty()
                || req.color == null || req.color.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Icon e color são obrigatórios");
        }
        if (categoryRepository.findByUserIdAndActiveTrueAndNameIgnoreCase(
                user.getId(), req.name.trim()).isPresent()) {
            return ResponseEntity.badRequest().body("Já existe categoria ativa com esse nome");
        }
        Category cat = newCategory(user.getId(), null,
                req.name.trim(), req.icon.trim(), req.color.trim());
        categoryRepository.save(cat);
        return ResponseEntity.ok(new CategoryResponse(cat));
    }

    /**
     * Cria uma cópia da categoria global para o user (RF-08). Idempotente:
     * se já existe cópia ativa para o par {@code (userId, globalId)}, devolve
     * 200 com a existente; se não, 201 com a nova. 404 se a global não existe.
     *
     * O body é opcional e pode trazer overrides para {@code icon} e {@code color}
     * — ausentes, a cópia herda os valores da global.
     */
    @PostMapping("/from-global/{globalId}")
    public ResponseEntity<?> copyFromGlobal(
            @AuthenticationPrincipal User user,
            @PathVariable UUID globalId,
            @RequestBody(required = false) CategoryRequest req) {

        Optional<CategoryGlobal> globalOpt = categoryGlobalRepository.findById(globalId);
        if (globalOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CategoryGlobal global = globalOpt.get();

        Optional<Category> existing = categoryRepository
                .findByUserIdAndGlobalIdAndActiveTrue(user.getId(), globalId);
        if (existing.isPresent()) {
            return ResponseEntity.ok(new CategoryResponse(existing.get()));
        }

        String icon = req != null && req.icon != null && !req.icon.isBlank()
                ? req.icon.trim() : global.getIcon();
        String color = req != null && req.color != null && !req.color.isBlank()
                ? req.color.trim() : global.getColor();

        Category copy = newCategory(user.getId(), globalId, global.getName(), icon, color);
        categoryRepository.save(copy);
        return ResponseEntity.status(201).body(new CategoryResponse(copy));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateCategory(
            @AuthenticationPrincipal User user, @PathVariable UUID id,
            @RequestBody CategoryRequest req) {
        Optional<Category> opt = categoryRepository.findById(id);
        if (opt.isEmpty() || !opt.get().isActive()
                || !opt.get().getUserId().equals(user.getId())) {
            return ResponseEntity.notFound().build();
        }
        Category cat = opt.get();
        // Cópias de globais (globalId != null) são editáveis — é justamente
        // o objetivo do RF-08 (T-035).
        if (req.name == null || req.name.trim().isEmpty() || req.name.trim().length() > 50) {
            return ResponseEntity.badRequest().body("Nome deve ter entre 1 e 50 caracteres");
        }
        if (req.icon == null || req.icon.trim().isEmpty()
                || req.color == null || req.color.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Icon e color são obrigatórios");
        }
        Optional<Category> sameName = categoryRepository
                .findByUserIdAndActiveTrueAndNameIgnoreCase(user.getId(), req.name.trim());
        if (sameName.isPresent() && !sameName.get().getId().equals(cat.getId())) {
            return ResponseEntity.badRequest().body("Já existe categoria ativa com esse nome");
        }
        cat.setName(req.name.trim());
        cat.setIcon(req.icon.trim());
        cat.setColor(req.color.trim());
        cat.setUpdatedAt(Instant.now());
        categoryRepository.save(cat);
        return ResponseEntity.ok(new CategoryResponse(cat));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(
            @AuthenticationPrincipal User user, @PathVariable UUID id) {
        Optional<Category> opt = categoryRepository.findById(id);
        if (opt.isEmpty() || !opt.get().isActive()
                || !opt.get().getUserId().equals(user.getId())) {
            return ResponseEntity.notFound().build();
        }
        Category cat = opt.get();
        // Cópias de globais também podem ser desativadas — o usuário decide
        // se quer "esconder" essa categoria da listagem.
        cat.setActive(false);
        cat.setUpdatedAt(Instant.now());
        categoryRepository.save(cat);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------

    private static Category newCategory(
            UUID userId, UUID globalId, String name, String icon, String color) {
        Category c = new Category();
        c.setUserId(userId);
        c.setGlobalId(globalId);
        c.setName(name);
        c.setIcon(icon);
        c.setColor(color);
        c.setActive(true);
        Instant now = Instant.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return c;
    }

    public static class CategoryRequest {
        public String name;
        public String icon;
        public String color;
    }

    public static class CategoryResponse {
        public UUID id;
        public String name;
        public String icon;
        public String color;
        public boolean isCustom;
        public boolean active;
        public UUID globalId;

        public CategoryResponse(CategoryGlobal g) {
            this.id = g.getId();
            this.name = g.getName();
            this.icon = g.getIcon();
            this.color = g.getColor();
            this.isCustom = false;
            this.active = true;
            this.globalId = null;
        }

        public CategoryResponse(Category c) {
            this.id = c.getId();
            this.name = c.getName();
            this.icon = c.getIcon();
            this.color = c.getColor();
            this.isCustom = true;
            this.active = c.isActive();
            this.globalId = c.getGlobalId();
        }
    }
}
