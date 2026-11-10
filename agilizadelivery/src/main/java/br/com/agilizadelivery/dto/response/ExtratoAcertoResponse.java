package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Extrato do entregador no periodo (UC03 passos 2 e 3, UC08).
 *
 * totalDevido e o valor que o proximo recibo tera; totalConsolidado, o que
 * ja esta em recibo; totalEmRevisao, o que aguarda a analise do operador.
 * possuiValorIndefinido sinaliza entrega fora de area em loja sem valor de
 * fora de area configurado, que precisa ser definido antes do acerto.
 */
public record ExtratoAcertoResponse(
        UUID motoboyId,
        String nomeEntregador,
        LocalDateTime periodoInicio,
        LocalDateTime periodoFim,
        List<LinhaExtratoResponse> linhas,
        BigDecimal totalDevido,
        BigDecimal totalConsolidado,
        BigDecimal totalEmRevisao,
        boolean possuiValorIndefinido) {
}
