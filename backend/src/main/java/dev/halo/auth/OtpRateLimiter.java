package dev.halo.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Rate limiter em memória para envio de OTP (RF-09).
 *
 * Usa Bucket4j com um bucket por telefone: 1 token, recarregado a cada 60s.
 * Isso garante o cooldown de 60s entre envios para o mesmo número.
 *
 * Armazenamento em {@link ConcurrentHashMap} — adequado para instância única.
 * Em ambiente multi-instância seria necessário backend distribuído (Redis).
 */
@Component
public class OtpRateLimiter {

    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Tenta consumir 1 token do bucket do telefone.
     *
     * @return {@code true} se o envio é permitido, {@code false} se ainda está em cooldown.
     */
    public boolean tryConsume(String phoneE164) {
        Bucket bucket = buckets.computeIfAbsent(phoneE164, this::newBucket);
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(String phone) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(1)
                .refillIntervally(1, COOLDOWN)
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
