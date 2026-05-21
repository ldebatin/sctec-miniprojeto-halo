package dev.halo.auth;

/**
 * Lançada quando uma solicitação de OTP é feita dentro do cooldown de 60s
 * para o mesmo telefone (RF-09).
 */
public class OtpCooldownException extends RuntimeException {

    private final String phone;

    public OtpCooldownException(String phone) {
        super("OTP cooldown ativo para o telefone: " + phone);
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }
}
