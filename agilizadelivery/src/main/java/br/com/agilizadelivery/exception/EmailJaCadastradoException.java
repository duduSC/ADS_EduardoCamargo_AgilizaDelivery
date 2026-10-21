package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada na tentativa de cadastrar um usuario com e-mail de login ja existente.
 *
 * Referencia: DVP UC07 e UsuarioRepository.existsByLoginEmail().
 */
public class EmailJaCadastradoException extends ConflitoDadosException {

    public EmailJaCadastradoException(String email) {
        super(
                String.format("O e-mail '%s' já está cadastrado para outro usuário.", email),
                CodigoErro.EMAIL_JA_CADASTRADO);
    }
}
