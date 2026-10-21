package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DistanciaPrecoResponse(
        UUID id,
        UUID estabelecimentoId,
        BigDecimal deKm,
        BigDecimal ateKm,
        BigDecimal valorPago,
        Boolean ativo,
        LocalDate vigenteDesde,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {
}
