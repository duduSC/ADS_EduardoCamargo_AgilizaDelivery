package br.com.agilizadelivery.service.rota;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado do calculo de uma rota com paradas.
 *
 * trechosKm traz a distancia de cada perna, na ordem: loja -> 1a parada,
 * 1a -> 2a, e assim por diante.
 */
public record RotaCalculada(BigDecimal distanciaTotalKm, List<BigDecimal> trechosKm) {
}
