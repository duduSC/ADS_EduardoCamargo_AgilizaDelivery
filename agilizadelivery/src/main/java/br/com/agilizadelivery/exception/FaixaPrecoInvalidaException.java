package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada na configuracao de faixas de preco quando as regras de integridade sao violadas:
 * faixas sobrepostas, lacunas entre faixas ou ate_km menor ou igual a de_km.
 *
 * Referencia: DVP UC05 E1, HU10 e DVP 1.5.4 (regras sem sobreposicao e sem lacunas).
 */
public class FaixaPrecoInvalidaException extends RegraDeNegocioException {

    public FaixaPrecoInvalidaException(String detalhe) {
        super(
                String.format("Configuração inválida de faixa de preço: %s.", detalhe),
                CodigoErro.FAIXA_PRECO_INVALIDA);
    }
}
