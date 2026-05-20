package dev.halo.ai;

/**
 * Status do resultado de uma chamada ao Gemini (analise-tecnica.md §3.2).
 *
 * Esses valores alimentam a taxa de erro do parser monitorada nas métricas do
 * PRD (≤ 5%).
 */
public enum AiLogStatus {
    /** Resposta válida (JSON parseado com sucesso ou {@code NOT_EXPENSE} reconhecido). */
    OK,
    /** Resposta recebida mas com JSON inválido — fallback heurístico assume. */
    INVALID_JSON,
    /** Erro de rede / HTTP — sem resposta. */
    ERROR
}
