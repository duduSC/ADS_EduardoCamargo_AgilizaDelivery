package br.com.agilizadelivery.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ordem de paradas proposta pelo entregador, apenas registrada. */
public record SugerirRotaRequest(
        @NotEmpty List<@NotNull UUID> entregaIds,
        @Size(max = 255) String observacao) {
}
