package br.com.agilizadelivery.exception;

import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando uma entidade ou recurso solicitado nao e encontrado no banco de dados.
 *
 * Mapeada para HTTP 404 (Not Found).
 */
public class EntidadeNaoEncontradaException extends AgilizaException {

    public EntidadeNaoEncontradaException(String mensagem) {
        super(mensagem, HttpStatus.NOT_FOUND, CodigoErro.RECURSO_NAO_ENCONTRADO);
    }

    public EntidadeNaoEncontradaException(String mensagem, CodigoErro codigoErro) {
        super(mensagem, HttpStatus.NOT_FOUND, codigoErro);
    }

    /**
     * Factory Method para padronizar mensagens de busca por ID.
     */
    public static EntidadeNaoEncontradaException porId(Class<?> tipoEntidade, Object id) {
        return new EntidadeNaoEncontradaException(
                String.format("%s não encontrado(a) com identificador: %s", tipoEntidade.getSimpleName(), id));
    }
}
