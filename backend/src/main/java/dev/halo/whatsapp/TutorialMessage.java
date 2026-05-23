package dev.halo.whatsapp;

/**
 * Texto compartilhado de "como usar o Halo" — usado no fluxo de boas-vindas
 * ({@code ConversationService}) e na resposta de ajuda quando a mensagem não
 * é reconhecida ({@code UnknownMessageHandler}).
 *
 * Centralizado pra que ambos os fluxos continuem mostrando os mesmos exemplos
 * (RF-03/RF-04 — feedback consistente).
 */
public final class TutorialMessage {

    private TutorialMessage() {}

    public static final String TEXT = """
            Aqui vai como usar o Halo:

            💸 Registrar um gasto
               _Mercado 87,30_
               _Uber 25 ontem_
               _Gasolina 150_

            📊 Ver resumo do mês
               _resumo_
               _resumo de abril_

            🌐 Acessar a web
               _site_""";
}
