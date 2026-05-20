package dev.halo.user;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lookup e criação de usuário por telefone (RF-01/RF-02, analise-tecnica.md §6.1).
 *
 * Esta classe cresceu em duas etapas:
 * <ul>
 *   <li>T-010 — {@link #findOrNull(String)} (telefone → {@link User} ou {@code null}).</li>
 *   <li>T-011 — {@link #create(String, String)} usado pelo fluxo conversacional
 *       {@code AWAITING_NAME} após o usuário responder com o nome.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PhoneNumberService phoneNumberService;

    /**
     * Devolve o usuário cujo {@code phone} bate com a entrada — normalizada
     * para E.164 — ou {@code null} se não existe.
     *
     * @throws InvalidPhoneException se a entrada não puder ser normalizada.
     */
    @Transactional(readOnly = true)
    public User findOrNull(String rawPhone) {
        String normalized = phoneNumberService.normalize(rawPhone);
        return userRepository.findByPhone(normalized).orElse(null);
    }

    /**
     * Cria um novo {@link User} com o telefone já normalizado em E.164 e o nome
     * informado pelo fluxo conversacional. {@code phoneE164} deve vir do
     * {@link PhoneNumberService} (não normaliza de novo aqui para tornar o
     * chamador responsável pela validação).
     */
    @Transactional
    public User create(String phoneE164, String name) {
        User user = new User();
        user.setPhone(phoneE164);
        user.setName(name);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }
}
