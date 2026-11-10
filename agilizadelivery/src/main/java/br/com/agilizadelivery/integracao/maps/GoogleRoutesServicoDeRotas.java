package br.com.agilizadelivery.integracao.maps;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.service.rota.ServicoDeRotas;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter da Google Routes API (computeRoutes) para a porta ServicoDeRotas.
 *
 * A field mask limita a resposta a distancia, unico dado usado pelo
 * sistema. Qualquer falha (chave ausente, erro HTTP, timeout, resposta
 * sem rota) resulta em Optional vazio: quem chama decide como seguir.
 */
@Slf4j
public class GoogleRoutesServicoDeRotas implements ServicoDeRotas {

    static final String FIELD_MASK = "routes.distanceMeters";
    private static final BigDecimal METROS_POR_KM = BigDecimal.valueOf(1000);

    private final GoogleMapsProperties properties;
    private final RestClient restClient;

    public GoogleRoutesServicoDeRotas(GoogleMapsProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public Optional<BigDecimal> distanciaKm(Coordenada origem, Coordenada destino) {
        if (!properties.possuiChave()) {
            log.warn("Chave da Google Maps API nao configurada; distancia nao calculada.");
            return Optional.empty();
        }
        if (origem == null || !origem.preenchida() || destino == null || !destino.preenchida()) {
            return Optional.empty();
        }

        try {
            RespostaRotas resposta = restClient.post()
                    .uri(properties.routesUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(Map.of(
                            "origin", waypoint(origem),
                            "destination", waypoint(destino),
                            "travelMode", "DRIVE",
                            "units", "METRIC"))
                    .retrieve()
                    .body(RespostaRotas.class);
            return converter(resposta);
        } catch (RestClientException e) {
            log.warn("Falha ao consultar a Google Routes API: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Map<String, Object> waypoint(Coordenada coordenada) {
        return Map.of("location", Map.of("latLng", Map.of(
                "latitude", coordenada.getLatitude(),
                "longitude", coordenada.getLongitude())));
    }

    private static Optional<BigDecimal> converter(RespostaRotas resposta) {
        if (resposta == null || resposta.routes() == null || resposta.routes().isEmpty()
                || resposta.routes().getFirst().distanceMeters() == null) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(resposta.routes().getFirst().distanceMeters())
                .divide(METROS_POR_KM, 2, RoundingMode.HALF_UP));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaRotas(List<Rota> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Rota(Long distanceMeters) {
    }
}
