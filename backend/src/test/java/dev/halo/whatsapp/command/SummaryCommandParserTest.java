package dev.halo.whatsapp.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Month;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit do {@link SummaryCommandParser} cobrindo variantes pt-BR (RF-15,
 * RF-16): meses completos/abreviados, com/sem ano, com/sem acentos.
 */
class SummaryCommandParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "resumo", "Resumo", "  resumo  ",
            "resumo do mês", "resumo deste mes", "resumo no mês",
            "quanto gastei", "Quanto gastei?"
    })
    void reconhece_triggers_sem_mes_usando_mes_corrente(String text) {
        SummaryCommandParser.Match match = SummaryCommandParser.match(text);

        assertThat(match.found()).isTrue();
        assertThat(match.targetMonth()).isNull();
        assertThat(SummaryCommandParser.parse(text))
                .hasValueSatisfying(ym -> assertThat(ym).isEqualTo(YearMonth.now()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "resumo abril", "resumo de abril", "Resumo de Abril",
            "RESUMO ABRIL", "resumo abr", "quanto gastei em abril"
    })
    void reconhece_mes_completo_e_abreviado_sem_ano(String text) {
        SummaryCommandParser.Match match = SummaryCommandParser.match(text);

        assertThat(match.found()).isTrue();
        assertThat(match.targetMonth()).isEqualTo(
                YearMonth.of(YearMonth.now().getYear(), Month.APRIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "resumo março", "resumo marco", "Resumo de Março", "resumo mar"
    })
    void mes_com_acento_e_sem_acento_resolve_para_o_mesmo_valor(String text) {
        assertThat(SummaryCommandParser.match(text).targetMonth())
                .isEqualTo(YearMonth.of(YearMonth.now().getYear(), Month.MARCH));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "resumo abril 2025", "resumo de abril de 2025",
            "Resumo de Abril 2025", "resumo abril/2025"
    })
    void reconhece_mes_com_ano(String text) {
        assertThat(SummaryCommandParser.match(text).targetMonth())
                .isEqualTo(YearMonth.of(2025, Month.APRIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"resumo abril 25", "resumo abril/25"})
    void reconhece_ano_de_dois_digitos_como_20xx(String text) {
        assertThat(SummaryCommandParser.match(text).targetMonth())
                .isEqualTo(YearMonth.of(2025, Month.APRIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jan", "fev", "mar", "abr", "mai", "jun",
                            "jul", "ago", "set", "out", "nov", "dez"})
    void aceita_todas_as_12_abreviacoes(String abbrev) {
        SummaryCommandParser.Match match = SummaryCommandParser.match("resumo " + abbrev);
        assertThat(match.found()).isTrue();
        assertThat(match.targetMonth()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
    })
    void aceita_todos_os_12_nomes_completos(String name) {
        SummaryCommandParser.Match match = SummaryCommandParser.match("resumo de " + name);
        assertThat(match.found()).isTrue();
        assertThat(match.targetMonth()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mercado 50", "uber 25,30", "oi",
            "preciso de ajuda", "como funciona", ""
    })
    void mensagens_que_nao_sao_resumo_nao_sao_reconhecidas(String text) {
        SummaryCommandParser.Match match = SummaryCommandParser.match(text);
        assertThat(match.found()).isFalse();
        assertThat(SummaryCommandParser.parse(text)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void texto_null_nao_quebra() {
        assertThat(SummaryCommandParser.match(null).found()).isFalse();
        assertThat(SummaryCommandParser.parse(null)).isEqualTo(Optional.empty());
    }

    @org.junit.jupiter.api.Test
    void texto_so_com_trigger_e_palavras_irreconheciveis_retorna_mes_corrente() {
        // "resumo xyz" — trigger ok, mas "xyz" não é mês. Tratamos como
        // pedido genérico (mês corrente) para ser tolerante.
        SummaryCommandParser.Match match = SummaryCommandParser.match("resumo xyz");
        assertThat(match.found()).isTrue();
        assertThat(match.targetMonth()).isNull(); // mês corrente
    }
}
