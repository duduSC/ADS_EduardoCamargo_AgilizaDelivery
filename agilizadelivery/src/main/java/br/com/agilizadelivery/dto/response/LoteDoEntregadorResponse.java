package br.com.agilizadelivery.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.entity.model.enums.StatusLote;

/**
 * Lote em execucao pelo entregador (UC02), com as entregas na ordem de
 * paradas definida pelo operador.
 */
public record LoteDoEntregadorResponse(
        UUID id,
        StatusLote status,
        Short qtdEntregas,
        LocalDateTime despachadoEm,
        LocalDateTime concluidoEm,
        CoordenadaDto coordenadaEstabelecimento,
        List<EntregaDoEntregadorResponse> entregas) {
}
