package br.com.agilizadelivery.service.rota;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

/**
 * Distancia aproximada loja -> cliente quando a chave da Google Maps
 * Platform nao esta configurada.
 *
 * PROVISORIO (D009): o DVP 1.5.5 pede a distancia do percurso viario.
 * Enquanto GOOGLE_MAPS_API_KEY estiver vazia, usa-se a distancia em linha
 * reta (Haversine) multiplicada por um fator fixo que aproxima o percurso
 * pelas ruas. Com a chave definida, este servico nao e usado.
 */
public class DistanciaEmLinhaReta implements ServicoDeRotas {

    static final double RAIO_TERRA_KM = 6371.0;
    static final BigDecimal FATOR_VIARIO = new BigDecimal("1.3");

    @Override
    public Optional<BigDecimal> distanciaKm(Coordenada origem, Coordenada destino) {
        if (origem == null || !origem.preenchida() || destino == null || !destino.preenchida()) {
            return Optional.empty();
        }
        double km = haversineKm(
                origem.getLatitude().doubleValue(), origem.getLongitude().doubleValue(),
                destino.getLatitude().doubleValue(), destino.getLongitude().doubleValue());
        return Optional.of(BigDecimal.valueOf(km)
                .multiply(FATOR_VIARIO)
                .setScale(2, RoundingMode.HALF_UP));
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RAIO_TERRA_KM * Math.asin(Math.sqrt(a));
    }
}
