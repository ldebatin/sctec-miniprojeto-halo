package dev.halo.whatsapp.conversation;

import dev.halo.user.User;
import dev.halo.user.UserRepository;
import dev.halo.user.UserService;
import dev.halo.whatsapp.EvolutionClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o cadastro conversacional via WhatsApp (RF-01, analise-tecnica.md §7.1).
 *
 * Protocolo (T-011):
 * <ol>
 *   <li>Telefone desconhecido envia qualquer mensagem → grava
 *       {@code conversation_state(AWAITING_NAME)} e responde "Qual seu nome?".</li>
 *   <li>Mesmo telefone responde com um nome (>= 2 chars) → cria o usuário,
 *       apaga o estado e envia "Bem-vindo(a), &lt;nome&gt;!".</li>
 *   <li>Nome muito curto → reenvia a pergunta sem alterar o estado.</li>
 *   <li>Estado com {@code expires_at &lt; now()} é descartado e a próxima mensagem
 *       reinicia o fluxo (TTL 15 min).</li>
 * </ol>
 *
 * Comando de link da web ("site", "link", etc.) é tratado em T-037 antes deste
 * serviço. Comandos "resumo" entram em T-038.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    /** TTL do estado conversacional — RF-01 (15 min). */
    static final Duration AWAITING_NAME_TTL = Duration.ofMinutes(15);

    /** Mínimo de 2 caracteres para aceitar o nome — RF-01. */
    static final int MIN_NAME_LENGTH = 2;

    private static final String ASK_NAME = "Olá! Eu sou o Halo. Qual seu nome?";

    private final ConversationStateRepository conversationStateRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EvolutionClient evolutionClient;

    /**
     * Processa uma mensagem recebida de um telefone que (a) não tem usuário ou
     * (b) está com cadastro pendente. Devolve {@code true} se o fluxo
     * conversacional consumiu a mensagem (não deve ir para o parser de gasto).
     *
     * @param phoneE164 telefone normalizado em E.164 (responsabilidade do chamador)
     * @param text      conteúdo da mensagem do usuário
     */
    @Transactional
    public boolean handleAwaitingName(String phoneE164, String text) {
        Optional<ConversationState> current = conversationStateRepository.findByPhone(phoneE164);

        if (current.isPresent() && current.get().getExpiresAt().isBefore(Instant.now())) {
            // flush garante que o DELETE chega ao banco antes do INSERT em
            // startAwaitingName, senão o UNIQUE em phone explode.
            conversationStateRepository.delete(current.get());
            conversationStateRepository.flush();
            current = Optional.empty();
        }

        if (current.isEmpty()) {
            startAwaitingName(phoneE164);
            return true;
        }

        return finishAwaitingName(current.get(), text);
    }

    private void startAwaitingName(String phoneE164) {
        ConversationState state = new ConversationState();
        state.setPhone(phoneE164);
        state.setState(ConversationStatus.AWAITING_NAME);
        Instant now = Instant.now();
        state.setUpdatedAt(now);
        state.setExpiresAt(now.plus(AWAITING_NAME_TTL));
        conversationStateRepository.save(state);

        evolutionClient.sendText(phoneE164, ASK_NAME);
        log.info("Cadastro iniciado AWAITING_NAME phone={}", phoneE164);
    }

    private boolean finishAwaitingName(ConversationState state, String rawText) {
        String name = rawText == null ? "" : rawText.trim();

        if (name.length() < MIN_NAME_LENGTH) {
            // Mantém o estado e refaz a pergunta.
            state.setUpdatedAt(Instant.now());
            conversationStateRepository.save(state);
            evolutionClient.sendText(state.getPhone(), ASK_NAME);
            log.info("Nome curto demais — reenviando pergunta phone={}", state.getPhone());
            return true;
        }

        User user = userService.create(state.getPhone(), name);
        // findByPhone garante consistência com a unique key — o save acima dispara flush.
        userRepository.flush();
        conversationStateRepository.delete(state);

        evolutionClient.sendText(state.getPhone(), "Bem-vindo(a), " + name + "!");
        log.info("Cadastro concluído phone={} userId={}", state.getPhone(), user.getId());
        return true;
    }
}
