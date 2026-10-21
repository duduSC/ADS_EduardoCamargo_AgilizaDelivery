package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada ao tentar despachar lote para um entregador que nao esteja apto
 * (por exemplo: cadastro BLOQUEADO ou status de disponibilidade diferente de ONLINE).
 *
 * Referencia: DVP UC01 E1 e HU02.
 */
public class MotoboyIndisponivelException extends RegraDeNegocioException {

    public MotoboyIndisponivelException(String nome, String motivo) {
        super(
                String.format("O entregador '%s' está indisponível para despacho: %s.", nome, motivo),
                CodigoErro.MOTOBOY_INDISPONIVEL);
    }
}
