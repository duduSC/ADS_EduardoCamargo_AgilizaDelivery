package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumo simples do dia para a operacao (D013, item 5).
 *
 * Considera as entregas finalizadas no dia, no horario de Sao Paulo.
 * valorTotalRepasse soma o repasse delas; valorPago, o das que estao em
 * recibo PAGO; valorAPagar e a diferenca. Um repasse indefinido (null)
 * deixa indefinidos o total e o a pagar, em vez de entrar como zero.
 */
public record RelatorioDoDiaResponse(
        LocalDate data,
        int totalEntregas,
        int entregues,
        int falhas,
        int canceladas,
        int entregadoresQueTrabalharam,
        BigDecimal valorTotalRepasse,
        BigDecimal valorPago,
        BigDecimal valorAPagar,
        List<RelatorioDoEntregadorResponse> porEntregador) {
}
