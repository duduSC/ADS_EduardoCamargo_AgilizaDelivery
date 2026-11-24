package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

/** Uma entrega consolidada num recibo do entregador (D013). */
public record EntregaDoReciboResponse(
        UUID entregaId,
        String nomeCliente,
        LocalDateTime finalizadaEm,
        StatusEntrega status,
        BigDecimal distanciaKm,
        BigDecimal valorRepasse) {
}
