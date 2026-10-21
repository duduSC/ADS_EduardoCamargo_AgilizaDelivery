package br.com.agilizadelivery.exception;

import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada em falhas de comunicacao com servicos externos (iFood, Google Maps Routes API, etc.).
 *
 * Mapeada para HTTP 502 (Bad Gateway).
 */
public class IntegracaoExternaException extends AgilizaException {

    public IntegracaoExternaException(String mensagem) {
        this(mensagem, null);
    }

    public IntegracaoExternaException(String mensagem, Throwable causa) {
        super(mensagem, causa, HttpStatus.BAD_GATEWAY, CodigoErro.INTEGRACAO_EXTERNA_ERRO);
    }
}
