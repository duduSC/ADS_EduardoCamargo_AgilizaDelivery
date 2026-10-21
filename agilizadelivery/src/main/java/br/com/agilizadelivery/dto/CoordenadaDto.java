package br.com.agilizadelivery.dto;

import java.math.BigDecimal;

public record CoordenadaDto(
        BigDecimal latitude,
        BigDecimal longitude) {
}
