package dev.halo.report;

import dev.halo.user.User;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de relatório (RF-12, RF-15, T-039).
 *
 * <ul>
 *   <li>{@code GET /reports/monthly?month=YYYY-MM} — total + breakdown
 *       por categoria + lista de gastos do mês.</li>
 *   <li>{@code GET /reports/categories?from=YYYY-MM-DD&to=YYYY-MM-DD} —
 *       total + breakdown por categoria no período.</li>
 * </ul>
 *
 * Ambas exigem JWT (config em {@code SecurityConfig.anyRequest().authenticated()})
 * e filtram pelo {@code userId} resolvido pelo {@code JwtAuthenticationFilter}.
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/monthly")
    public ResponseEntity<?> monthly(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "month", required = false) String monthParam) {

        YearMonth month;
        try {
            month = monthParam != null && !monthParam.isBlank()
                    ? YearMonth.parse(monthParam)
                    : YearMonth.now();
        } catch (DateTimeParseException e) {
            log.debug("month inválido: {}", monthParam);
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(reportService.monthly(user.getId(), month));
    }

    @GetMapping("/categories")
    public ResponseEntity<?> categories(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "from")
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to")
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(reportService.categories(user.getId(), from, to));
    }
}
