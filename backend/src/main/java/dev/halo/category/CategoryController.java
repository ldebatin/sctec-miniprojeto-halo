package dev.halo.category;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import dev.halo.user.User;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private CategoryGlobalRepository categoryGlobalRepository;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories(@AuthenticationPrincipal User user) {
        List<CategoryGlobal> globals = categoryGlobalRepository.findAll();
        List<Category> customs = categoryRepository.findByUserIdAndActiveTrue(user.getId());
        List<CategoryResponse> globalResponses = globals.stream()
            .map(g -> new CategoryResponse(g, false))
            .toList();
        List<CategoryResponse> customResponses = customs.stream()
            .map(c -> new CategoryResponse(c, true))
            .toList();
        List<CategoryResponse> all = new ArrayList<>();
        all.addAll(globalResponses);
        all.addAll(customResponses);
        return ResponseEntity.ok(all);
    }

    public static class CategoryRequest {
        public String name;
        public String icon;
        public String color;
    }

    @PostMapping
    public ResponseEntity<?> createCategory(@AuthenticationPrincipal User user, @RequestBody CategoryRequest req) {
        if (req.name == null || req.name.trim().isEmpty() || req.name.trim().length() > 50) {
            return ResponseEntity.badRequest().body("Nome deve ter entre 1 e 50 caracteres");
        }
        if (req.icon == null || req.icon.trim().isEmpty() || req.color == null || req.color.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Icon e color são obrigatórios");
        }
        if (categoryRepository.findByUserIdAndActiveTrueAndNameIgnoreCase(user.getId(), req.name.trim()).isPresent()) {
            return ResponseEntity.badRequest().body("Já existe categoria ativa com esse nome");
        }
        Category cat = new Category();
        cat.setUserId(user.getId());
        cat.setName(req.name.trim());
        cat.setIcon(req.icon.trim());
        cat.setColor(req.color.trim());
        cat.setActive(true);
        cat.setCreatedAt(java.time.Instant.now());
        cat.setUpdatedAt(java.time.Instant.now());
        categoryRepository.save(cat);
        return ResponseEntity.ok(new CategoryResponse(cat, true));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateCategory(@AuthenticationPrincipal User user, @PathVariable UUID id, @RequestBody CategoryRequest req) {
        Optional<Category> opt = categoryRepository.findById(id);
        if (opt.isEmpty() || !opt.get().isActive() || !opt.get().getUserId().equals(user.getId())) {
            return ResponseEntity.notFound().build();
        }
        Category cat = opt.get();
        if (cat.getGlobalId() != null) {
            return ResponseEntity.status(403).body("Não é permitido editar categorias globais");
        }
        if (req.name == null || req.name.trim().isEmpty() || req.name.trim().length() > 50) {
            return ResponseEntity.badRequest().body("Nome deve ter entre 1 e 50 caracteres");
        }
        if (req.icon == null || req.icon.trim().isEmpty() || req.color == null || req.color.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Icon e color são obrigatórios");
        }
        Optional<Category> sameName = categoryRepository.findByUserIdAndActiveTrueAndNameIgnoreCase(user.getId(), req.name.trim());
        if (sameName.isPresent() && !sameName.get().getId().equals(cat.getId())) {
            return ResponseEntity.badRequest().body("Já existe categoria ativa com esse nome");
        }
        cat.setName(req.name.trim());
        cat.setIcon(req.icon.trim());
        cat.setColor(req.color.trim());
        cat.setUpdatedAt(java.time.Instant.now());
        categoryRepository.save(cat);
        return ResponseEntity.ok(new CategoryResponse(cat, true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        Optional<Category> opt = categoryRepository.findById(id);
        if (opt.isEmpty() || !opt.get().isActive() || !opt.get().getUserId().equals(user.getId())) {
            return ResponseEntity.notFound().build();
        }
        Category cat = opt.get();
        if (cat.getGlobalId() != null) {
            return ResponseEntity.status(403).body("Não é permitido deletar categorias globais");
        }
        cat.setActive(false);
        cat.setUpdatedAt(java.time.Instant.now());
        categoryRepository.save(cat);
        return ResponseEntity.noContent().build();
    }

    public static class CategoryResponse {
        public UUID id;
        public String name;
        public String icon;
        public String color;
        public boolean isCustom;
        public boolean active;
        public CategoryResponse(CategoryGlobal g, boolean isCustom) {
            this.id = g.getId();
            this.name = g.getName();
            this.icon = g.getIcon();
            this.color = g.getColor();
            this.isCustom = isCustom;
            this.active = true;
        }
        public CategoryResponse(Category c, boolean isCustom) {
            this.id = c.getId();
            this.name = c.getName();
            this.icon = c.getIcon();
            this.color = c.getColor();
            this.isCustom = isCustom;
            this.active = c.isActive();
        }
    }
}
