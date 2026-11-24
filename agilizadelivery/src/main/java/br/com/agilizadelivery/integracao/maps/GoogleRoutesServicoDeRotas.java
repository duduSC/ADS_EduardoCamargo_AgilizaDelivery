package br.com.agilizadelivery.integracao.maps;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
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
 *
 * O percurso viario (DVP 1.5.5, passo 1) e o da moto: travelMode
 * TWO_WHEELER, configuravel em agiliza.maps.modo-viagem (D013). Onde o
 * Google nao tiver rota de moto para o trecho (resposta sem rota, ou 400
 * de modo indisponivel na regiao), tenta uma vez com DRIVE. Chave
 * recusada, cota, erro do servidor e timeout nao tem reserva: a segunda
 * chamada falharia igual e dobraria a espera.
 *
 * O log registra o modo usado, nunca as coordenadas do cliente.
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

        String modo = properties.modoViagem();
        Consulta consulta = consultar(origem, destino, modo);
        if (consulta.distancia().isPresent()) {
            log.info("Routes API: distancia calculada com travelMode {}.", modo);
            return consulta.distancia();
        }
        if (!consulta.semRotaNoModo() || GoogleMapsProperties.MODO_CARRO.equals(modo)) {
            return Optional.empty();
        }

        log.info("Routes API sem rota com travelMode {}; tentando {}.", modo, GoogleMapsProperties.MODO_CARRO);
        Consulta reserva = consultar(origem, destino, GoogleMapsProperties.MODO_CARRO);
        if (reserva.distancia().isPresent()) {
            log.info("Routes API: distancia calculada com travelMode {} (reserva).", GoogleMapsProperties.MODO_CARRO);
        }
        return reserva.distancia();
    }

    /**
     * Uma chamada a computeRoutes. semRotaNoModo indica que a API respondeu,
     * mas sem rota para este modo (resposta sem rota ou 400), que e o caso
     * em que outro modo pode dar certo.
     */
    private Consulta consultar(Coordenada origem, Coordenada destino, String modo) {
        try {
            RespostaRotas resposta = restClient.post()
                    .uri(properties.routesUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(Map.of(
                            "origin", waypoint(origem),
                            "destination", waypoint(destino),
                            "travelMode", modo,
                            "units", "METRIC"))
                    .retrieve()
                    .body(RespostaRotas.class);
            Optional<BigDecimal> distancia = converter(resposta);
            return new Consulta(distancia, distancia.isEmpty());
        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("Routes API recusou o travelMode {} (HTTP 400).", modo);
            return new Consulta(Optional.empty(), true);
        } catch (HttpStatusCodeException e) {
            log.warn("Falha ao consultar a Google Routes API com travelMode {}: HTTP {}.",
                    modo, e.getStatusCode().value());
            return new Consulta(Optional.empty(), false);
        } catch (RestClientException e) {
            log.warn("Falha ao consultar a Google Routes API com travelMode {}: {}.",
                    modo, e.getClass().getSimpleName());
            return new Consulta(Optional.empty(), false);
        }
    }

    private record Consulta(Optional<BigDecimal> distancia, boolean semRotaNoModo) {
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
