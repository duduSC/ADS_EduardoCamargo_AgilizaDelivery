package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada ao tentar realizar operacoes ou autenticar usuarios vinculados
 * a um estabelecimento que se encontra suspenso ou inativo.
 *
 * Referencia: DVP UC06, UC07 E2 e HU12.
 */
public class EstabelecimentoInativoException extends AcessoNegadoException {

    public EstabelecimentoInativoException(String nomeEstabelecimento) {
        super(
                String.format("O estabelecimento '%s' está inativo e seu acesso ou operação está suspenso.",
                        nomeEstabelecimento),
                CodigoErro.ESTABELECIMENTO_INATIVO);
    }
}
