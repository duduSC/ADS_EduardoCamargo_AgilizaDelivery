package br.com.agilizadelivery.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.Provedor;

public record ConfiguracaoIntegracaoResponse(
        UUID id,
        UUID estabelecimentoId,
        Provedor provedor,
        String merchantId,
        Boolean ativo,
        LocalDateTime ultimoPollingEm,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {
}
