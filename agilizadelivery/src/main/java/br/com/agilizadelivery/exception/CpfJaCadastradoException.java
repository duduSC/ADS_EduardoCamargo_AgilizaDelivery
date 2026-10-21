package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada na tentativa de cadastrar um motoboy com CPF ja existente no sistema
 * (detectado atraves da comparacao de indice cego cpf_hash).
 *
 * Referencia: DVP UC05 E2 e HU09.
 */
public class CpfJaCadastradoException extends ConflitoDadosException {

    public CpfJaCadastradoException() {
        super(
                "Já existe um entregador cadastrado no sistema com o CPF informado.",
                CodigoErro.CPF_JA_CADASTRADO);
    }
}
