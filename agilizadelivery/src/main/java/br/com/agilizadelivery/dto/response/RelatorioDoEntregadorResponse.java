package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linha do relatorio do dia por entregador (D013, item 5). entregas
 * inclui as canceladas depois do despacho; os valores seguem a mesma
 * regra dos totais do dia.
 */
public record RelatorioDoEntregadorResponse(
        UUID motoboyId,
        String nome,
        int entregas,
        int entregues,
        int falhas,
        BigDecimal valorRepasse,
        BigDecimal valorPago,
        BigDecimal valorAPagar) {
}
