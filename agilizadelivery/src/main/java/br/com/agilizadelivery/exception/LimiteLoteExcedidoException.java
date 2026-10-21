package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando o operador tenta agrupar mais entregas em um lote
 * do que o limite maximo parametrizado no estabelecimento (max_pedidos_por_lote).
 *
 * Referencia: DVP UC01 E2 e HU02.
 */
public class LimiteLoteExcedidoException extends RegraDeNegocioException {

    public LimiteLoteExcedidoException(int qtdSelecionada, short maxPermitido) {
        super(
                String.format("A quantidade de entregas selecionadas (%d) excede o limite máximo permitido de %d por lote.",
                        qtdSelecionada, maxPermitido),
                CodigoErro.LIMITE_LOTE_EXCEDIDO);
    }
}
