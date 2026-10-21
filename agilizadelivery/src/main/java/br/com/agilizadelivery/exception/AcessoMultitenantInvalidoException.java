package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando uma operacao tenta acessar, consultar ou modificar dados
 * pertencentes a outro restaurante parceiro, violando o isolamento logico da multilocacao.
 *
 * Referencia: DVP 1.2.1, RNF05 e HU13.
 */
public class AcessoMultitenantInvalidoException extends AcessoNegadoException {

    public AcessoMultitenantInvalidoException(String recurso) {
        super(
                String.format("Acesso negado: o recurso '%s' não pertence ao seu estabelecimento.", recurso),
                CodigoErro.VIOLACAO_MULTITENANT);
    }
}
