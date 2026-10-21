package br.com.agilizadelivery.dto.request;

import java.time.LocalDateTime;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlterarStatusEntregaRequest(
        @NotNull StatusEntrega status,
        @Size(max = 100) String motivoFalha,
        @Valid CoordenadaDto coordenada,
        @NotNull LocalDateTime ocorridoEm) {
}
