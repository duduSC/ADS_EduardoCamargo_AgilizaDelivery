package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada ao tentar ajustar manualmente o valor de repasse de uma entrega
 * quando as condicoes de alteracao nao sao satisfeitas (por exemplo: caixa fechado
 * ou entrega vinculada a recibo imutavel).
 *
 * Referencia: DVP UC03 A1 e Entrega.permiteAjusteDeValor().
 */
public class AjusteValorNaoPermitidoException extends RegraDeNegocioException {

    public AjusteValorNaoPermitidoException(String motivo) {
        super(
                String.format("Ajuste manual de repasse não permitido: %s.", motivo),
                CodigoErro.AJUSTE_VALOR_NAO_PERMITIDO);
    }
}
