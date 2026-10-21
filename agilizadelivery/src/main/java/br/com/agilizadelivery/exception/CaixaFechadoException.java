package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando uma operacao que requer caixa aberto (como ajuste de repasse,
 * geracao de recibo ou cancelamento de acerto) e executada com o caixa ja fechado.
 *
 * Referencia: DVP Caixa.java e secao 1.5.5, passo 6.
 */
public class CaixaFechadoException extends RegraDeNegocioException {

    public CaixaFechadoException(String operacao) {
        super(
                String.format("Operação '%s' não permitida: o caixa do período de operação já está fechado.", operacao),
                CodigoErro.CAIXA_FECHADO);
    }
}
