package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.StatusRecibo;

/**
 * Recibo como o aplicativo do entregador o lista (D013, extensao do UC08).
 * Sem os identificadores internos do caixa e do operador que o gerou.
 */
public record ReciboDoEntregadorResponse(
        UUID id,
        LocalDate dataReferencia,
        LocalDateTime periodoInicio,
        LocalDateTime periodoFim,
        Short qtdEntregas,
        BigDecimal valorTotal,
        StatusRecibo status,
        LocalDateTime pagoEm,
        LocalDateTime criadoEm) {
}
