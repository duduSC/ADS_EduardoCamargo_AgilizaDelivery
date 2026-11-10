package br.com.agilizadelivery.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * A ordem de entregaIds e a ordem das paradas definida pelo operador:
 * a primeira posicao e a primeira parada a partir da loja. O
 * agrupamento e a sequencia sao manuais (DVP 1.2.4).
 */
public record DespacharLoteRequest(
        @NotNull UUID motoboyId,
        @NotEmpty List<@NotNull UUID> entregaIds) {
}
