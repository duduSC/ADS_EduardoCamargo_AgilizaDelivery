package br.com.agilizadelivery.exception;

import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Token ausente, malformado, adulterado, expirado ou do tipo errado.
 *
 * A mensagem e unica para todos esses casos: o cliente so precisa saber
 * que deve autenticar de novo. HTTP 401.
 */
public class TokenInvalidoException extends AgilizaException {

    public static final String MENSAGEM = "Sessão inválida ou expirada. Faça login novamente.";

    public TokenInvalidoException() {
        super(MENSAGEM, HttpStatus.UNAUTHORIZED, CodigoErro.NAO_AUTENTICADO);
    }
}
