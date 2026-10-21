package br.com.agilizadelivery.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AjustarRepasseRequest(
        @NotNull @PositiveOrZero BigDecimal valorNovo,
        @NotBlank @Size(max = 255) String motivo) {
}
