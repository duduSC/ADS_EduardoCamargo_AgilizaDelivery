package br.com.agilizadelivery.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;


public record ConfiguracaoIntegracaoResponse(
        UUID id,
        UUID estabelecimentoId,
        String provedor,
        String merchantId,
        Boolean ativo,
        LocalDateTime ultimoPollingEm,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {
}
