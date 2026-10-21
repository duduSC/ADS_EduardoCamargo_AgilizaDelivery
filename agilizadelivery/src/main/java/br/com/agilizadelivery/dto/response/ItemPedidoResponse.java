package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemPedidoResponse(
        Long id,
        UUID pedidoExternoId,
        String descricao,
        Short quantidade,
        BigDecimal valorUnitario,
        String observacao) {
}
