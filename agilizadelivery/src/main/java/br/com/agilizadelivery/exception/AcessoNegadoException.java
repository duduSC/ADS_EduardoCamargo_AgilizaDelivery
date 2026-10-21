package br.com.agilizadelivery.exception;

import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando o usuario autenticado nao possui autorizacao para a acao
 * ou tenta acessar recursos fora de sua alçada ou de outro inquilino (multitenancy).
 *
 * Mapeada para HTTP 403 (Forbidden).
 */
public class AcessoNegadoException extends AgilizaException {

    public AcessoNegadoException(String mensagem) {
        this(mensagem, CodigoErro.ACESSO_NEGADO);
    }

    public AcessoNegadoException(String mensagem, CodigoErro codigoErro) {
        super(mensagem, HttpStatus.FORBIDDEN, codigoErro);
    }
}
