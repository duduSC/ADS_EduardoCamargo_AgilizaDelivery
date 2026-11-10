package br.com.agilizadelivery.dto.response;

import java.util.UUID;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

public record ParadaRotaResponse(
        Short ordem,
        UUID entregaId,
        String nomeCliente,
        String telefoneCliente,
        String enderecoCompleto,
        CoordenadaDto coordenada,
        StatusEntrega status) {
}
