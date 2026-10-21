package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.StatusRecibo;

public record ReciboResponse(
        UUID id,
        UUID estabelecimentoId,
        UUID caixaId,
        UUID motoboyId,
        UUID usuarioGeradorId,
        LocalDate dataReferencia,
        LocalDateTime periodoInicio,
        LocalDateTime periodoFim,
        Short qtdEntregas,
        BigDecimal valorTotal,
        StatusRecibo status,
        LocalDateTime pagoEm,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {
}
