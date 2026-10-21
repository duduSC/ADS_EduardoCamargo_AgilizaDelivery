package br.com.agilizadelivery.exception;

import java.time.LocalDate;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada ao tentar abrir um novo caixa quando ja existe um caixa aberto
 * ou registrado para o estabelecimento na mesma data de referencia.
 *
 * Referencia: DVP Caixa.java e uk_caixa_estabelecimento_data.
 */
public class CaixaJaAbertoException extends ConflitoDadosException {

    public CaixaJaAbertoException(LocalDate dataReferencia) {
        super(
                String.format("Já existe um caixa registrado para este estabelecimento na data de referência %s.",
                        dataReferencia),
                CodigoErro.CAIXA_JA_ABERTO);
    }
}
