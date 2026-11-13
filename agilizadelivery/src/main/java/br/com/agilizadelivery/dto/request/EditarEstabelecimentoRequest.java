package br.com.agilizadelivery.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Campos editaveis pelo administrador da loja (UC05, parametrizacao).
 * Todos opcionais; o servico aplica somente os que vierem nao nulos.
 */
public record EditarEstabelecimentoRequest(
        @Positive Short maxPedidosPorLote,
        @PositiveOrZero BigDecimal valorRepasseForaDeArea,
        @Size(max = 15) String telefone) {
}
