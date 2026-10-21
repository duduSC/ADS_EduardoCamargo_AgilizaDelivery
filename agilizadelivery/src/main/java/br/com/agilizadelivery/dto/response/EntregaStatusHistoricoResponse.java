package br.com.agilizadelivery.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

public record EntregaStatusHistoricoResponse(
        Long id,
        UUID entregaId,
        StatusEntrega statusAnterior,
        StatusEntrega statusNovo,
        UUID registradoPorUsuarioId,
        CoordenadaDto coordenada,
        LocalDateTime registradoEm) {
}
