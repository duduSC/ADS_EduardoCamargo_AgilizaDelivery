package br.com.agilizadelivery.exception;

import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando uma operacao conflita com o estado persistente atual
 * (como duplicidade de chave unica ou violacao de idempotencia).
 *
 * Mapeada para HTTP 409 (Conflict).
 */
public class ConflitoDadosException extends AgilizaException {

    public ConflitoDadosException(String mensagem) {
        this(mensagem, CodigoErro.DADO_DUPLICADO);
    }

    public ConflitoDadosException(String mensagem, CodigoErro codigoErro) {
        super(mensagem, HttpStatus.CONFLICT, codigoErro);
    }
}
