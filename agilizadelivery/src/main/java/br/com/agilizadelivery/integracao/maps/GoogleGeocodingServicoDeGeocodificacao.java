package br.com.agilizadelivery.integracao.maps;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.service.rota.ServicoDeGeocodificacao;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter da Google Geocoding API para a porta ServicoDeGeocodificacao.
 *
 * Qualquer falha (chave ausente, erro HTTP, status diferente de OK,
 * resposta sem resultado) resulta em Optional vazio.
 */
@Slf4j
@Component
public class GoogleGeocodingServicoDeGeocodificacao implements ServicoDeGeocodificacao {

    private final GoogleMapsProperties properties;
    private final RestClient restClient;

    @Autowired
    public GoogleGeocodingServicoDeGeocodificacao(GoogleMapsProperties properties) {
        this(properties, RestClient.builder().requestFactory(fabricaComTimeout(properties)));
    }

    GoogleGeocodingServicoDeGeocodificacao(GoogleMapsProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public Optional<Coordenada> geocodificar(String endereco) {
        if (!properties.possuiChave()) {
            log.warn("Chave da Google Maps API nao configurada; endereco nao geocodificado.");
            return Optional.empty();
        }
        if (endereco == null || endereco.isBlank()) {
            return Optional.empty();
        }

        try {
            RespostaGeocodificacao resposta = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(properties.geocodingUrl())
                            .queryParam("address", endereco)
                            .queryParam("region", "br")
                            .queryParam("language", "pt-BR")
                            .queryParam("key", properties.apiKey())
                            .encode()
                            .build()
                            .toUri())
                    .retrieve()
                    .body(RespostaGeocodificacao.class);
            return converter(resposta);
        } catch (RestClientException e) {
            log.warn("Falha ao consultar a Google Geocoding API: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<Coordenada> converter(RespostaGeocodificacao resposta) {
        if (resposta == null || !"OK".equals(resposta.status())
                || resposta.results() == null || resposta.results().isEmpty()) {
            return Optional.empty();
        }
        Resultado resultado = resposta.results().getFirst();
        if (resultado.geometry() == null || resultado.geometry().location() == null) {
            return Optional.empty();
        }
        Localizacao local = resultado.geometry().location();
        if (local.lat() == null || local.lng() == null) {
            return Optional.empty();
        }
        return Optional.of(new Coordenada(local.lat(), local.lng()));
    }

    private static SimpleClientHttpRequestFactory fabricaComTimeout(GoogleMapsProperties properties) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(properties.timeout());
        fabrica.setReadTimeout(properties.timeout());
        return fabrica;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaGeocodificacao(String status, List<Resultado> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultado(Geometria geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometria(Localizacao location) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Localizacao(BigDecimal lat, BigDecimal lng) {
    }
}
