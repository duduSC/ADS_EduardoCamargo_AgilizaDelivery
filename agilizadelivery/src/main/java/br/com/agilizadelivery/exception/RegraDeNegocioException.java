package br.com.agilizadelivery.exception;

import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando uma regra ou invariante de dominio e violada.
 *
 * Mapeada para HTTP 422 (Unprocessable Entity).
 */
public class RegraDeNegocioException extends AgilizaException {

    public RegraDeNegocioException(String mensagem) {
        this(mensagem, CodigoErro.REGRA_NEGOCIO_VIOLADA);
    }

    public RegraDeNegocioException(String mensagem, CodigoErro codigoErro) {
        super(mensagem, HttpStatus.UNPROCESSABLE_ENTITY, codigoErro);
    }

    public RegraDeNegocioException(String mensagem, Throwable causa, CodigoErro codigoErro) {
        super(mensagem, causa, HttpStatus.UNPROCESSABLE_ENTITY, codigoErro);
    }
}
