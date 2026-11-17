package br.com.agilizadelivery.dto.response;

import java.time.LocalDate;

import br.com.agilizadelivery.entity.model.enums.StatusCaixa;

/**
 * Acerto do proprio entregador (UC08), com a situacao do caixa do dia.
 *
 * statusCaixa e nulo quando a loja ainda nao abriu o caixa do dia.
 * contestacaoPermitida e verdadeiro so com o caixa ABERTO ou REABERTO
 * (UC08 E2); com o caixa FECHADO os valores sao finais.
 */
public record AcertoDoEntregadorResponse(
        ExtratoAcertoResponse extrato,
        LocalDate dataReferenciaCaixa,
        StatusCaixa statusCaixa,
        boolean contestacaoPermitida) {
}
