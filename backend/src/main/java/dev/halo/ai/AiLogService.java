package dev.halo.ai;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste uma linha em {@code ai_log} para cada chamada ao Gemini (T-017).
 *
 * Cada {@code record} roda em uma transação nova ({@code REQUIRES_NEW}) para
 * que o log sobreviva mesmo se a transação externa (parser → expense) rolar
 * back por outro motivo — a observabilidade vale por si só.
 */
@Service
@RequiredArgsConstructor
public class AiLogService {

    private final AiLogRepository repository;

    /**
     * Registra uma chamada. {@code userId} pode ser {@code null} (quando o
     * webhook chega de um telefone ainda não cadastrado).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiLog record(
            UUID userId,
            String model,
            String prompt,
            int tokensIn,
            int tokensOut,
            int latencyMs,
            AiLogStatus status) {
        AiLog log = new AiLog();
        log.setUserId(userId);
        log.setModel(model);
        log.setPromptHash(sha256(prompt));
        log.setTokensIn(tokensIn);
        log.setTokensOut(tokensOut);
        log.setLatencyMs(latencyMs);
        log.setStatus(status);
        log.setCostEst(GeminiPricing.estimate(tokensIn, tokensOut));
        log.setCreatedAt(Instant.now());
        return repository.save(log);
    }

    /** SHA-256 hex (64 chars) — empacotado pra reuso interno e testes. */
    static String sha256(String input) {
        if (input == null) input = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é mandatório no JRE; impossível em runtime real.
            throw new IllegalStateException("SHA-256 indisponível no JRE", e);
        }
    }

    /** Visível para teste: expõe o cálculo de custo sem ter que persistir. */
    static BigDecimal estimateCost(int tokensIn, int tokensOut) {
        return GeminiPricing.estimate(tokensIn, tokensOut);
    }
}
