package dev.halo.expense;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTOs HTTP do {@code /expenses} (RF-05, RF-13, RF-14 — T-024).
 *
 * Os DTOs ficam num único arquivo para manter a vizinhança visível: as
 * regras de validação do POST e do PATCH evoluem juntas.
 */
final class ExpenseDtos {

    private ExpenseDtos() {}

    /** Body de {@code POST /expenses}: todos os campos obrigatórios. */
    record CreateRequest(
            @NotBlank @Size(min = 1, max = 200) String description,
            @NotNull @DecimalMin(value = "0.01", message = "amount deve ser > 0") BigDecimal amount,
            @NotNull UUID categoryId,
            @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate occurredAt
    ) {}

    /**
     * Body de {@code PATCH /expenses/{id}}: todos os campos opcionais — o
     * controller só atualiza os que vierem não-nulos. Quando presentes,
     * passam pelas mesmas validações do POST.
     */
    record UpdateRequest(
            @Size(min = 1, max = 200) String description,
            @DecimalMin(value = "0.01", message = "amount deve ser > 0") BigDecimal amount,
            UUID categoryId,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate occurredAt
    ) {}

    /** Representação devolvida em todas as rotas de leitura/escrita. */
    record Response(
            UUID id,
            String description,
            BigDecimal amount,
            UUID categoryId,
            LocalDate occurredAt,
            ExpenseSource source,
            Instant createdAt,
            Instant updatedAt
    ) {
        static Response from(Expense e) {
            return new Response(
                    e.getId(),
                    e.getDescription(),
                    e.getAmount(),
                    e.getCategoryId(),
                    e.getOccurredAt(),
                    e.getSource(),
                    e.getCreatedAt(),
                    e.getUpdatedAt());
        }
    }
}
