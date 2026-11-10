package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.CoordenadaDto;

public record RotaLoteResponse(
        UUID loteId,
        CoordenadaDto origem,
        BigDecimal distanciaRotaKm,
        List<ParadaRotaResponse> paradas) {
}
