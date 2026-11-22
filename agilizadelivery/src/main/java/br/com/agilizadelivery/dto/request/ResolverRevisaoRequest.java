package br.com.agilizadelivery.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Resolucao de contestacao pelo operador. Se valorRepasse for informado
 * e diferente do atual, aplica ajuste (UC03 A1). Caso contrario, apenas
 * limpa a pendencia.
 */
public record ResolverRevisaoRequest(
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal valorRepasse,
        @NotBlank @Size(max = 100) String motivo) {
}
