package br.com.agilizadelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Vinculo da loja ao provedor (UC05 passo 5).
 *
 * provedor e texto (DVP 3.0); o servico normaliza para maiusculas e recusa
 * valor fora de service.support.Provedores com 400 DADOS_INVALIDOS.
 */
public record SalvarConfiguracaoIntegracaoRequest(
        @NotBlank @Size(max = 20) String provedor,
        @NotBlank @Size(max = 100) String merchantId) {
}
