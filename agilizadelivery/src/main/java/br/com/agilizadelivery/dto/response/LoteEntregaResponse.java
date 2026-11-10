package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.StatusLote;

public record LoteEntregaResponse(
        UUID id,
        UUID estabelecimentoId,
        UUID motoboyId,
        UUID usuarioDespachanteId,
        StatusLote status,
        Short qtdEntregas,
        BigDecimal valorTotalRepasse,
        LocalDateTime despachadoEm,
        LocalDateTime concluidoEm,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {
}
