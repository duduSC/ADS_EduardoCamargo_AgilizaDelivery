package br.com.agilizadelivery.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SalvarDistanciaPrecoRequest(
        @NotNull @PositiveOrZero @Digits(integer = 3, fraction = 2) BigDecimal deKm,
        @NotNull @Positive @Digits(integer = 3, fraction = 2) BigDecimal ateKm,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal valorPago,
        @NotNull LocalDate vigenteDesde) {
}
