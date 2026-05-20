package dev.halo.user;

/**
 * Lançada por {@link PhoneNumberService#normalize(String)} quando a entrada
 * não pode ser normalizada para E.164 (vazia, sem dígitos suficientes, etc.).
 *
 * Quem consome a normalização (ex.: webhook do Evolution) deve tratar a exceção
 * para não derrubar o processamento — ver
 * {@code InboundMessageService.record}.
 */
public class InvalidPhoneException extends RuntimeException {
    public InvalidPhoneException(String message) {
        super(message);
    }
}
