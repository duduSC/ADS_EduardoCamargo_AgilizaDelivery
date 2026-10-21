package br.com.agilizadelivery.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record DespacharLoteRequest(
        @NotNull UUID motoboyId,
        @NotEmpty List<@NotNull UUID> entregaIds) {
}
