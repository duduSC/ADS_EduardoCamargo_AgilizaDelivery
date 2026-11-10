package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SugestaoRotaResponse(
        Long id,
        UUID loteId,
        UUID motoboyId,
        List<UUID> entregaIds,
        BigDecimal distanciaSugeridaKm,
        String observacao,
        LocalDateTime sugeridaEm) {
}
