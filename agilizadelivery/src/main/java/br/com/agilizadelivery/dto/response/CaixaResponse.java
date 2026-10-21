package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.StatusCaixa;

public record CaixaResponse(
        UUID id,
        UUID estabelecimentoId,
        LocalDate dataReferencia,
        LocalDateTime abertoEm,
        LocalDateTime fechadoEm,
        UUID usuarioFechamentoId,
        StatusCaixa status,
        Short qtdRecibos,
        BigDecimal valorTotal,
        LocalDateTime reabertoEm,
        UUID usuarioReaberturaId,
        String motivoReabertura,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {
}
