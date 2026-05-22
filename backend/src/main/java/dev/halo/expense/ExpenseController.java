package dev.halo.expense;

import dev.halo.category.Category;
import dev.halo.category.CategoryRepository;
import dev.halo.user.User;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD HTTP de {@link Expense} (RF-05, RF-13, RF-14 — T-024).
 *
 * Todas as rotas exigem JWT (config global em
 * {@code common.security.SecurityConfig}). Para evitar vazamento de
 * existência, acessar/editar/apagar um gasto de outro usuário devolve
 * 404 — não 403.
 *
 * Esta task (T-024) só faz: CRUD + filtros + paginação + soft delete.
 * Pré-resolução de categoria por nome (helper {@code resolveCategoryForUser}
 * usado em T-014) continua restrita ao fluxo do WhatsApp.
 */
@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;

    @GetMapping
    public ResponseEntity<PagedResponse> list(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "from", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "category_id", required = false) UUID categoryId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "occurredAt", "createdAt"));

        Specification<Expense> spec = ExpenseSpecifications.ownedBy(user.getId())
                .and(ExpenseSpecifications.notDeleted())
                .and(ExpenseSpecifications.between(from, to))
                .and(ExpenseSpecifications.categoryEquals(categoryId))
                .and(ExpenseSpecifications.descriptionContains(q));

        Page<Expense> result = expenseRepository.findAll(spec, pageable);

        List<ExpenseResponse> content = result.getContent().stream()
                .map(ExpenseResponse::from)
                .toList();
        return ResponseEntity.ok(new PagedResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()));
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ExpenseDtos.CreateRequest request) {

        Category category = findOwnedCategoryOrNull(user, request.categoryId());
        if (category == null) {
            return ResponseEntity.badRequest().build();
        }

        Instant now = Instant.now();
        Expense expense = new Expense();
        expense.setUserId(user.getId());
        expense.setCategoryId(category.getId());
        expense.setDescription(request.description().trim());
        expense.setAmount(request.amount());
        expense.setOccurredAt(request.occurredAt());
        expense.setSource(ExpenseSource.WEB);
        expense.setCreatedAt(now);
        expense.setUpdatedAt(now);

        Expense saved = expenseRepository.save(expense);
        return ResponseEntity.status(201).body(ExpenseResponse.from(saved));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> findOne(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        return expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(id, user.getId())
                .map(e -> ResponseEntity.ok(ExpenseResponse.from(e)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ExpenseResponse> update(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody ExpenseDtos.UpdateRequest request) {

        Optional<Expense> opt = expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(id, user.getId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Expense expense = opt.get();

        if (request.description() != null) {
            expense.setDescription(request.description().trim());
        }
        if (request.amount() != null) {
            expense.setAmount(request.amount());
        }
        if (request.occurredAt() != null) {
            expense.setOccurredAt(request.occurredAt());
        }
        if (request.categoryId() != null) {
            Category category = findOwnedCategoryOrNull(user, request.categoryId());
            if (category == null) {
                return ResponseEntity.badRequest().build();
            }
            expense.setCategoryId(category.getId());
        }
        expense.setUpdatedAt(Instant.now());

        Expense saved = expenseRepository.save(expense);
        return ResponseEntity.ok(ExpenseResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {

        Optional<Expense> opt = expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(id, user.getId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Expense expense = opt.get();
        Instant now = Instant.now();
        expense.setDeletedAt(now);
        expense.setUpdatedAt(now);
        expenseRepository.save(expense);
        return ResponseEntity.noContent().build();
    }

    /**
     * Garante que {@code categoryId} aponta para uma categoria do usuário e
     * ativa. Devolve {@code null} se não encontrar — o controller mapeia
     * para 400 sem revelar qual usuário é o dono real.
     */
    private Category findOwnedCategoryOrNull(User user, UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(c -> c.isActive() && user.getId().equals(c.getUserId()))
                .orElse(null);
    }

    /** Envelope simples para resposta paginada — evita a verbosidade do {@code Page}. */
    record PagedResponse(
            List<ExpenseResponse> content,
            int page,
            int size,
            long total
    ) {}
}
