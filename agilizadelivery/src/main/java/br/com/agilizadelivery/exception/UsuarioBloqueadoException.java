package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando um usuario inativo ou motoboy com cadastro BLOQUEADO tenta
 * autenticar-se ou realizar operacoes no sistema.
 *
 * Referencia: DVP UC07 E3 e HU09.
 */
public class UsuarioBloqueadoException extends AcessoNegadoException {

    public UsuarioBloqueadoException(String identificador, String motivo) {
        super(
                String.format("O acesso do usuário '%s' foi recusado: %s.", identificador, motivo),
                CodigoErro.USUARIO_BLOQUEADO);
    }
}
