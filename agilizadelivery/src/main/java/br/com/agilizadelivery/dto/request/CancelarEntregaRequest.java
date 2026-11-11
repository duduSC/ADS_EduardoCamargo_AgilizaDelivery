package br.com.agilizadelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo do cancelamento pelo operador (UC02 A3). */
public record CancelarEntregaRequest(
        @NotBlank @Size(max = 100) String motivo) {
}
