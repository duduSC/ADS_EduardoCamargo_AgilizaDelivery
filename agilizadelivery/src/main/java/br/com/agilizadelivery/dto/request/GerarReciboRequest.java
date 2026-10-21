package br.com.agilizadelivery.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record GerarReciboRequest(
        @NotNull UUID motoboyId,
        @NotNull LocalDateTime periodoInicio,
        @NotNull LocalDateTime periodoFim) {
}
