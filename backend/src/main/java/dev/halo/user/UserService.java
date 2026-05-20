package dev.halo.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lookup de usuário por telefone (RF-02, analise-tecnica.md §6.1).
 *
 * Esta task (T-010) só faz: normalizar o telefone com {@link PhoneNumberService}
 * e devolver o {@link User} se já existe ({@code null} caso contrário).
 *
 * A criação de usuário (cadastro conversacional {@code AWAITING_NAME}) entra
 * na T-011.
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
}
