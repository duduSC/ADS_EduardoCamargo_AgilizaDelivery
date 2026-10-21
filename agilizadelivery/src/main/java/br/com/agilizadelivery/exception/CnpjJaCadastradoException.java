package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada na tentativa de cadastrar um restaurante com CNPJ ja existente.
 *
 * Referencia: DVP UC06 E1 e HU11.
 */
public class CnpjJaCadastradoException extends ConflitoDadosException {

    public CnpjJaCadastradoException(String cnpj) {
        super(
                String.format("Já existe um estabelecimento cadastrado com o CNPJ informado: %s.", cnpj),
                CodigoErro.CNPJ_JA_CADASTRADO);
    }
}
