package br.com.agilizadelivery.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * A ordem de entregaIds e a rota principal definida pelo operador:
 * a primeira posicao e a primeira parada a partir da loja.
 */
public record DespacharLoteRequest(
        @NotNull UUID motoboyId,
        @NotEmpty List<@NotNull UUID> entregaIds) {
}
