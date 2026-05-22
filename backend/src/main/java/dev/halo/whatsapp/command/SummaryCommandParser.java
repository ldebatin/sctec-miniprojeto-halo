package dev.halo.whatsapp.command;

import java.text.Normalizer;
import java.time.Month;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Reconhece comandos de resumo no WhatsApp (RF-15, RF-16 — T-038):
 * {@code resumo}, {@code resumo do mês}, {@code resumo <mês>},
 * {@code resumo <mês> <ano>}, {@code quanto gastei}.
 *
 * Comparação é feita após normalização (lowercase + remoção de acentos),
 * para casar tanto "Resumo de Março" quanto "resumo marco".
 *
 * Devolve {@link Optional} contendo o {@link YearMonth} alvo, ou
 * {@link Optional#empty} se a mensagem não é um comando de resumo. Sem
 * mês informado, usa o mês corrente; ano omitido usa o ano corrente.
 */
@Slf4j
public final class SummaryCommandParser {

    private static final Set<String> TRIGGERS = Set.of("resumo", "quanto gastei");
    private static final Set<String> STOPWORDS = Set.of(
            "de", "do", "no", "em", "este", "esse", "deste", "desse",
            "neste", "nesse", "o", "a", "mes");
    private static final Map<String, Month> MONTHS_BY_NAME = monthsByName();

    private SummaryCommandParser() {}

    /**
     * Resultado da parse. {@code found=false} significa "não é um comando";
     * {@code targetMonth=null} significa "comando reconhecido mas sem mês —
     * use o corrente".
     */
    public record Match(boolean found, YearMonth targetMonth) {
        public static Match notFound() { return new Match(false, null); }
        public static Match currentMonth() { return new Match(true, null); }
        public static Match of(YearMonth ym) { return new Match(true, ym); }
    }

    /** Devolve a {@link Match} com o {@link YearMonth} já resolvido em UTC. */
    public static Optional<YearMonth> parse(String text) {
        Match match = match(text);
        if (!match.found()) return Optional.empty();
        return Optional.of(match.targetMonth() != null
                ? match.targetMonth() : YearMonth.now());
    }

    /** Variante crua, sem expandir o {@code null} de mês corrente. */
    public static Match match(String text) {
        if (text == null) return Match.notFound();
        String normalized = normalize(text);
        if (normalized.isEmpty()) return Match.notFound();

        String remainder = stripTrigger(normalized);
        if (remainder == null) return Match.notFound();
        if (remainder.isEmpty()) return Match.currentMonth();

        Month month = null;
        Integer year = null;
        for (String token : remainder.split("\\s+|/|-")) {
            if (token.isBlank()) continue;
            if (STOPWORDS.contains(token)) continue;
            if (token.matches("\\d{4}")) {
                year = Integer.parseInt(token);
                continue;
            }
            if (token.matches("\\d{2}")) {
                year = 2000 + Integer.parseInt(token);
                continue;
            }
            Month found = MONTHS_BY_NAME.get(token);
            if (found != null) {
                month = found;
            }
        }

        if (month == null) {
            // Reconheceu o trigger mas o resto não decodificou pra mês —
            // ainda assim é um comando de resumo (mês corrente).
            return Match.currentMonth();
        }
        int resolvedYear = year != null ? year : YearMonth.now().getYear();
        return Match.of(YearMonth.of(resolvedYear, month));
    }

    private static String stripTrigger(String text) {
        for (String trigger : TRIGGERS) {
            if (text.equals(trigger)) return "";
            if (text.startsWith(trigger + " ")) {
                return text.substring(trigger.length()).trim();
            }
        }
        return null;
    }

    /** Lowercase + remove acentos + remove pontuação. */
    static String normalize(String s) {
        String lower = s.toLowerCase(Locale.ROOT).trim();
        String stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped.replaceAll("[?!.,;:]", "").trim();
    }

    private static Map<String, Month> monthsByName() {
        return Map.<String, Month>ofEntries(
                Map.entry("janeiro", Month.JANUARY),  Map.entry("jan", Month.JANUARY),
                Map.entry("fevereiro", Month.FEBRUARY), Map.entry("fev", Month.FEBRUARY),
                Map.entry("marco", Month.MARCH),        Map.entry("mar", Month.MARCH),
                Map.entry("abril", Month.APRIL),        Map.entry("abr", Month.APRIL),
                Map.entry("maio", Month.MAY),           Map.entry("mai", Month.MAY),
                Map.entry("junho", Month.JUNE),         Map.entry("jun", Month.JUNE),
                Map.entry("julho", Month.JULY),         Map.entry("jul", Month.JULY),
                Map.entry("agosto", Month.AUGUST),      Map.entry("ago", Month.AUGUST),
                Map.entry("setembro", Month.SEPTEMBER), Map.entry("set", Month.SEPTEMBER),
                Map.entry("outubro", Month.OCTOBER),    Map.entry("out", Month.OCTOBER),
                Map.entry("novembro", Month.NOVEMBER),  Map.entry("nov", Month.NOVEMBER),
                Map.entry("dezembro", Month.DECEMBER),  Map.entry("dez", Month.DECEMBER));
    }
}
