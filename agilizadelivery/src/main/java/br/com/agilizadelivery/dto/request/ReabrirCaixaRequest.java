package br.com.agilizadelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReabrirCaixaRequest(
        @NotBlank @Size(max = 255) String motivo) {
}
