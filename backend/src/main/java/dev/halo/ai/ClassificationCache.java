package dev.halo.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cache in-memory das classificações por descrição normalizada
 * (RF-03 / analise-tecnica §9.4 — T-043).
 *
 * <p>Chave: {@code userId + "|" + sha256_hex(normalized(text))}, onde
 * {@code normalized} = lowercase + remoção de acentos + remoção de
 * dígitos/pontuação + colapso de espaços. Manter o {@code userId} no
 * prefixo permite que categorias customizadas de um usuário não vazem
 * para classificações de outro, e simplifica a invalidação por usuário.
 *
 * <p>Valor: {@code categoryHint} (nome textual da categoria sugerido pela IA).
 * Quando há hit, o {@code WhatsappExpenseParser} monta o
 * {@link ExpenseParseResult} usando o {@code AmountExtractor} para o
 * valor — economizando uma chamada ao Gemini (zero token).
 *
 * <p>Configuração: TTL 30 dias, capacidade máxima 10k entradas (LRU
 * gerenciada pelo Caffeine via Window TinyLFU). Stats logadas a cada
 * {@link #STATS_LOG_INTERVAL} chamadas para análise de hit rate.
 */
@Component
@Slf4j
public class ClassificationCache {

    static final Duration TTL = Duration.ofDays(30);
    static final long MAX_SIZE = 10_000;
    static final int STATS_LOG_INTERVAL = 100;

    private final Cache<String, String> cache;
    private final AtomicLong totalLookups = new AtomicLong();

    public ClassificationCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterWrite(TTL)
                .recordStats()
                .build();
    }

    /**
     * Devolve o {@code categoryHint} cacheado para o par
     * {@code (userId, descriçãoNormalizada)}. Loga estatísticas
     * periodicamente para análise de hit rate.
     */
    public Optional<String> getHint(UUID userId, String text) {
        String key = buildKey(userId, text);
        String hit = cache.getIfPresent(key);
        long total = totalLookups.incrementAndGet();
        if (total % STATS_LOG_INTERVAL == 0) {
            CacheStats stats = cache.stats();
            log.info("ClassificationCache stats lookups={} hits={} misses={} hitRate={}",
                    total, stats.hitCount(), stats.missCount(),
                    String.format(Locale.ROOT, "%.2f%%", stats.hitRate() * 100));
        }
        return Optional.ofNullable(hit);
    }

    /** Armazena o {@code categoryHint} resolvido pela IA. */
    public void putHint(UUID userId, String text, String categoryHint) {
        if (categoryHint == null || categoryHint.isBlank()) return;
        cache.put(buildKey(userId, text), categoryHint);
    }

    /**
     * Invalida todas as entradas do usuário. Chamado quando categorias
     * do usuário são criadas/renomeadas/desativadas — a sugestão da IA
     * pode passar a apontar para uma categoria diferente.
     */
    public void invalidate(UUID userId) {
        String prefix = userId + "|";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        log.debug("ClassificationCache invalidated userId={}", userId);
    }

    /** Acesso direto às stats (útil pra testes e Actuator futuramente). */
    public CacheStats stats() {
        return cache.stats();
    }

    long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    static String buildKey(UUID userId, String text) {
        return userId + "|" + hashOf(normalize(text));
    }

    /** Lowercase + remoção de acentos + remoção de dígitos/pontuação. */
    static String normalize(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase(Locale.ROOT);
        String stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped
                .replaceAll("[\\d.,;:!?()$/\\-_]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String hashOf(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
