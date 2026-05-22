package dev.halo.expense;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTOs de requisição HTTP do {@code /expenses} (RF-05, RF-13, RF-14 — T-024).
 *
 * A resposta foi extraída para {@link ExpenseResponse} (público) porque
 * é reusada por outros módulos como {@code report/} em T-039.
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
}
