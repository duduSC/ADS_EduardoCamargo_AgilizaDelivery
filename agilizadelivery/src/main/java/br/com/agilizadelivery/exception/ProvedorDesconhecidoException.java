package br.com.agilizadelivery.exception;

import java.util.Collection;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada quando o provedor de pedidos informado nao esta entre os
 * aceitos pela aplicacao (DVP 1.5.4: provedor VARCHAR(20), valores validados
 * pela aplicacao).
 *
 * Mapeada para HTTP 400 (Bad Request) com DADOS_INVALIDOS, o mesmo codigo
 * que o Bean Validation usa para um campo com valor invalido.
 */
public class ProvedorDesconhecidoException extends AgilizaException {

    public ProvedorDesconhecidoException(String provedor, Collection<String> aceitos) {
        super(
                String.format("Provedor '%s' não é aceito. Valores aceitos: %s.",
                        provedor, String.join(", ", new TreeSet<>(aceitos))),
                HttpStatus.BAD_REQUEST,
                CodigoErro.DADOS_INVALIDOS);
    }
}
