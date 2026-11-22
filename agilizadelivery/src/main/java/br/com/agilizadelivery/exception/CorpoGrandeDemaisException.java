package br.com.agilizadelivery.exception;

import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Corpo da requisicao acima de agiliza.http.tamanho-maximo-corpo (T014).
 */
public class CorpoGrandeDemaisException extends AgilizaException {

    public CorpoGrandeDemaisException() {
        super("O corpo da requisição é maior que o limite permitido.",
                HttpStatus.CONTENT_TOO_LARGE, CodigoErro.CORPO_REQUISICAO_INVALIDO);
    }
}
