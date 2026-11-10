package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

/**
 * Uma entrega do extrato, com a distancia, a faixa aplicada e o valor
 * (HU06, HU14). Sem faixa e com distancia, o valor e o de fora de area.
 */
public record LinhaExtratoResponse(
        UUID entregaId,
        String nomeCliente,
        LocalDateTime finalizadaEm,
        StatusEntrega status,
        BigDecimal distanciaKm,
        BigDecimal faixaDeKm,
        BigDecimal faixaAteKm,
        boolean foraDeArea,
        BigDecimal valorRepasse,
        SituacaoAcerto situacao,
        UUID reciboId) {
}
