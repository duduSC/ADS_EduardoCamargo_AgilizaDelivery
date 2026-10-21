package br.com.agilizadelivery.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SalvarDistanciaPrecoRequest(
        @NotNull @PositiveOrZero BigDecimal deKm,
        @NotNull @Positive BigDecimal ateKm,
        @NotNull @Positive BigDecimal valorPago,
        @NotNull LocalDate vigenteDesde) {
}
