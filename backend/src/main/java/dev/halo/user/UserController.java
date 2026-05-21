package dev.halo.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil do usuário autenticado (RF-17, analise-tecnica.md §11.5).
 *
 * Esta task (T-023) só faz: devolver os dados do {@link User} resolvido
 * pelo {@code JwtAuthenticationFilter} (T-021) e permitir atualizar o
 * {@code name}. {@code phone} é imutável — qualquer campo no body que
 * não seja {@code name} é silenciosamente ignorado.
 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(MeResponse.from(user));
    }

    @PatchMapping
    public ResponseEntity<MeResponse> update(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateMeRequest request) {

        user.setName(request.name().trim());
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);
        return ResponseEntity.ok(MeResponse.from(saved));
    }

    public record UpdateMeRequest(
            @NotBlank @Size(min = 2, max = 120, message = "name deve ter entre 2 e 120 caracteres") String name
    ) {}

    public record MeResponse(UUID id, String name, String phone, Instant createdAt) {
        static MeResponse from(User user) {
            return new MeResponse(user.getId(), user.getName(), user.getPhone(), user.getCreatedAt());
        }
    }
}
