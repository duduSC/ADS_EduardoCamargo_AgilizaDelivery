package br.com.agilizadelivery.tempo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Unica fonte de "agora" e de "hoje" do negocio.
 *
 * As datas do contrato sao locais, sem fuso, e os clientes as leem e
 * enviam no horario de Sao Paulo. Por isso nunca se usa o fuso padrao da
 * JVM, que no container e UTC: das 21:00 as 23:59 ele ja esta no dia
 * seguinte, e o caixa do dia seria outro.
 *
 * PROVISORIO (T014): o DVP nao define fuso; um unico para todas as lojas,
 * o do contrato com os clientes.
 *
 * Fica fora das camadas porque as entidades (@PrePersist), os servicos e
 * os controllers usam o mesmo relogio.
 */
public final class HorarioLocal {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private HorarioLocal() {
    }

    public static LocalDateTime agora() {
        return LocalDateTime.now(FUSO);
    }

    public static LocalDate hoje() {
        return LocalDate.now(FUSO);
    }
}
