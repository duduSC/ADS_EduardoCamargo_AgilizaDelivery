package br.com.agilizadelivery.service.support;

import java.math.BigDecimal;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.exception.RegraDeNegocioException;

/** Validacao das coordenadas recebidas pelos servicos. */
public final class Coordenadas {

    private static final BigDecimal LATITUDE_MAXIMA = BigDecimal.valueOf(90);
    private static final BigDecimal LONGITUDE_MAXIMA = BigDecimal.valueOf(180);

    private Coordenadas() {
    }

    /** Cria a coordenada, recusando valores ausentes ou fora das faixas validas. */
    public static Coordenada validada(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null
                || latitude.abs().compareTo(LATITUDE_MAXIMA) > 0
                || longitude.abs().compareTo(LONGITUDE_MAXIMA) > 0) {
            throw new RegraDeNegocioException(
                    "Coordenada inválida: latitude deve estar entre -90 e 90 e longitude entre -180 e 180.");
        }
        return new Coordenada(latitude, longitude);
    }
}
