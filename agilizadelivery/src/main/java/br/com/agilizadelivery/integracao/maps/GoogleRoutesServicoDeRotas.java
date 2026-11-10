package br.com.agilizadelivery.integracao.maps;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.agilizadelivery.service.rota.PontoRota;
import br.com.agilizadelivery.service.rota.RotaCalculada;
import br.com.agilizadelivery.service.rota.ServicoDeRotas;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter da Google Routes API (computeRoutes) para a porta ServicoDeRotas.
 *
 * As paradas viram "intermediates" na ordem recebida, e a ultima delas e
 * o destino. A field mask limita a resposta as distancias, unico dado
 * usado pelo sistema.
 *
 * Qualquer falha (chave ausente, erro HTTP, timeout, resposta sem rota)
 * resulta em Optional vazio: quem chama decide como seguir.
 */
@Slf4j
@Component
public class GoogleRoutesServicoDeRotas implements ServicoDeRotas {

    static final String FIELD_MASK = "routes.distanceMeters,routes.legs.distanceMeters";
    private static final BigDecimal METROS_POR_KM = BigDecimal.valueOf(1000);

    private final GoogleMapsProperties properties;
    private final RestClient restClient;

    @Autowired
    public GoogleRoutesServicoDeRotas(GoogleMapsProperties properties) {
        this(properties, RestClient.builder().requestFactory(fabricaComTimeout(properties)));
    }

    GoogleRoutesServicoDeRotas(GoogleMapsProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public Optional<BigDecimal> distanciaKm(PontoRota origem, PontoRota destino) {
        return calcularRota(origem, List.of(destino)).map(RotaCalculada::distanciaTotalKm);
    }

    @Override
    public Optional<RotaCalculada> calcularRota(PontoRota origem, List<PontoRota> paradas) {
        if (!properties.possuiChave()) {
            log.warn("Chave da Google Maps API nao configurada; distancia nao calculada.");
            return Optional.empty();
        }
        if (origem == null || !origem.localizavel() || paradas == null || paradas.isEmpty()
                || paradas.stream().anyMatch(parada -> parada == null || !parada.localizavel())) {
            return Optional.empty();
        }

        try {
            RespostaRotas resposta = restClient.post()
                    .uri(properties.routesUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(montarCorpo(origem, paradas))
                    .retrieve()
                    .body(RespostaRotas.class);
            return converter(resposta);
        } catch (RestClientException e) {
            log.warn("Falha ao consultar a Google Routes API: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Map<String, Object> montarCorpo(PontoRota origem, List<PontoRota> paradas) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("origin", waypoint(origem));
        corpo.put("destination", waypoint(paradas.getLast()));
        if (paradas.size() > 1) {
            corpo.put("intermediates", paradas.subList(0, paradas.size() - 1).stream()
                    .map(GoogleRoutesServicoDeRotas::waypoint)
                    .toList());
        }
        corpo.put("travelMode", "DRIVE");
        corpo.put("languageCode", "pt-BR");
        corpo.put("units", "METRIC");
        return corpo;
    }

    private static Map<String, Object> waypoint(PontoRota ponto) {
        if (ponto.temCoordenada()) {
            return Map.of("location", Map.of("latLng", Map.of(
                    "latitude", ponto.coordenada().getLatitude(),
                    "longitude", ponto.coordenada().getLongitude())));
        }
        return Map.of("address", ponto.endereco());
    }

    private static Optional<RotaCalculada> converter(RespostaRotas resposta) {
        if (resposta == null || resposta.routes() == null || resposta.routes().isEmpty()) {
            return Optional.empty();
        }
        Rota rota = resposta.routes().getFirst();
        if (rota.distanceMeters() == null) {
            return Optional.empty();
        }
        List<BigDecimal> trechos = rota.legs() == null
                ? List.of()
                : rota.legs().stream().map(trecho -> emKm(trecho.distanceMeters())).toList();
        return Optional.of(new RotaCalculada(emKm(rota.distanceMeters()), trechos));
    }

    private static BigDecimal emKm(Long metros) {
        long valor = metros == null ? 0L : metros;
        return BigDecimal.valueOf(valor).divide(METROS_POR_KM, 2, RoundingMode.HALF_UP);
    }

    private static SimpleClientHttpRequestFactory fabricaComTimeout(GoogleMapsProperties properties) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(properties.timeout());
        fabrica.setReadTimeout(properties.timeout());
        return fabrica;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaRotas(List<Rota> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Rota(Long distanceMeters, List<Trecho> legs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Trecho(Long distanceMeters) {
    }
}
