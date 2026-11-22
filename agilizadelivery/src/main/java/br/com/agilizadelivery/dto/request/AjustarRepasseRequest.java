package br.com.agilizadelivery.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AjustarRepasseRequest(
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal valorNovo,
        @NotBlank @Size(max = 255) String motivo) {
}
