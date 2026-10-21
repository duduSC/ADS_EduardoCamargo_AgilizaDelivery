package br.com.agilizadelivery.dto.request;

import br.com.agilizadelivery.entity.model.enums.Provedor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalvarConfiguracaoIntegracaoRequest(
        @NotNull Provedor provedor,
        @NotBlank @Size(max = 100) String merchantId) {
}
