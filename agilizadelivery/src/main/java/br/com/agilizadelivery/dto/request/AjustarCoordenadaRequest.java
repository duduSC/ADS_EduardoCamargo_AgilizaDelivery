package br.com.agilizadelivery.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/** Reposicionamento manual do pino da entrega (UC01 A1, risco R04). */
public record AjustarCoordenadaRequest(
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude) {
}
