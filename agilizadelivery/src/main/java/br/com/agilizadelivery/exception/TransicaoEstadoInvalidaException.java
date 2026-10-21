package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando uma transicao na maquina de estados de negocio e invalida
 * (por exemplo: tentar despachar entrega cancelada ou reabrir caixa em estado ilegal).
 *
 * Referencia: DVP secao 1.4.4 (Maquina de Estados dos Objetos de Negocio).
 */
public class TransicaoEstadoInvalidaException extends RegraDeNegocioException {

    public TransicaoEstadoInvalidaException(String entidade, Object statusAtual, Object novoStatus) {
        super(
                String.format("Transição de estado inválida para %s: não é permitido alterar de '%s' para '%s'.",
                        entidade, statusAtual, novoStatus),
                CodigoErro.TRANSICAO_ESTADO_INVALIDA);
    }

    public TransicaoEstadoInvalidaException(String mensagem) {
        super(mensagem, CodigoErro.TRANSICAO_ESTADO_INVALIDA);
    }
}
