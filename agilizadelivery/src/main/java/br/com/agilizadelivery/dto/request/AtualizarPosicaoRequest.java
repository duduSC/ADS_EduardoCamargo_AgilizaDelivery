package br.com.agilizadelivery.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/**
 * Posicao GPS enviada pelo aplicativo do entregador.
 */
public record AtualizarPosicaoRequest(
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude) {
}
