package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada ao tentar alterar ou cancelar um recibo que nao permite modificacoes
 * (por exemplo: caixa do dia ja fechado ou recibo ja cancelado).
 *
 * Referencia: DVP UC03 A2 e Recibo.podeSerCancelado().
 */
public class ReciboInalteravelException extends RegraDeNegocioException {

    public ReciboInalteravelException(String motivo) {
        super(
                String.format("O recibo não pode ser modificado ou cancelado: %s.", motivo),
                CodigoErro.RECIBO_INALTERAVEL);
    }
}
