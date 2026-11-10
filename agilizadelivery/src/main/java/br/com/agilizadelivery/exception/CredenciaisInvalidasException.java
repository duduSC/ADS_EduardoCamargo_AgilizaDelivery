package br.com.agilizadelivery.exception;

import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando o e-mail ou a senha nao conferem.
 *
 * A mensagem e generica e nao indica qual dos dois esta errado, para nao
 * revelar se o e-mail existe na plataforma.
 *
 * Referencia: DVP UC07 E1 e HU13.
 */
public class CredenciaisInvalidasException extends AgilizaException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha inválidos.", HttpStatus.UNAUTHORIZED, CodigoErro.CREDENCIAIS_INVALIDAS);
    }
}
