package br.com.agilizadelivery.exception;

import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando um valor de entrada e aceito pelo formato, mas nao
 * pelo contexto da operacao (ex.: o entregador pedir a disponibilidade
 * EM_ROTA, que e controlada pelo lote).
 *
 * Mapeada para HTTP 400 (Bad Request), com DADOS_INVALIDOS ou
 * PARAMETRO_INVALIDO, os mesmos codigos usados pelo Bean Validation e pelos
 * parametros de query.
 */
public class RequisicaoInvalidaException extends AgilizaException {

    public RequisicaoInvalidaException(String mensagem) {
        this(mensagem, CodigoErro.DADOS_INVALIDOS);
    }

    public RequisicaoInvalidaException(String mensagem, CodigoErro codigoErro) {
        super(mensagem, HttpStatus.BAD_REQUEST, codigoErro);
    }
}
