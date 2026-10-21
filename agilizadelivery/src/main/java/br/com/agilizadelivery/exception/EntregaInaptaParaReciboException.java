package br.com.agilizadelivery.exception;

import java.util.UUID;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando uma entrega nao cumpre os criterios para entrar em um recibo de acerto:
 * nao estar em estado final, estar pendente de revisao ou ja estar vinculada a outro recibo.
 *
 * Referencia: DVP UC03 E1, HU07 e Entrega.podeEntrarEmRecibo().
 */
public class EntregaInaptaParaReciboException extends RegraDeNegocioException {

    public EntregaInaptaParaReciboException(UUID entregaId, String motivo) {
        super(
                String.format("A entrega com ID '%s' não pode entrar em recibo de acerto: %s.", entregaId, motivo),
                CodigoErro.ENTREGA_INAPTA_RECIBO);
    }
}
