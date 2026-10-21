package br.com.agilizadelivery.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.EtapaFalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.StatusFalhaIntegracao;

public record FalhaIntegracaoResponse(
        Long id,
        UUID configuracaoIntegracaoId,
        String idExterno,
        EtapaFalhaIntegracao etapa,
        Short tentativas,
        StatusFalhaIntegracao status,
        LocalDateTime ocorridoEm,
        LocalDateTime resolvidoEm) {
}
