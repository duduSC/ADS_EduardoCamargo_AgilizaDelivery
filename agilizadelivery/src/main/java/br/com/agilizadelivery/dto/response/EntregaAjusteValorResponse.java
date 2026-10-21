package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EntregaAjusteValorResponse(
        Long id,
        UUID entregaId,
        UUID usuarioId,
        BigDecimal valorAnterior,
        BigDecimal valorNovo,
        String motivo,
        LocalDateTime ajustadoEm) {
}
