package br.com.agilizadelivery.exception;

import java.util.UUID;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando uma entrega nao preenche os requisitos para inclusao em um lote
 * (por exemplo: status diferente de AGUARDANDO_DESPACHO ou pertencente a outro estabelecimento).
 *
 * Referencia: DVP UC01 e HU01.
 */
public class EntregaInaptaParaLoteException extends RegraDeNegocioException {

    public EntregaInaptaParaLoteException(UUID entregaId, String motivo) {
        super(
                String.format("A entrega com ID '%s' não pode ser incluída no lote: %s.", entregaId, motivo),
                CodigoErro.ENTREGA_INAPTA_LOTE);
    }
}
