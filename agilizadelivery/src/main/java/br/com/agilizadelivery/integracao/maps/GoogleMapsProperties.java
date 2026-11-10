package br.com.agilizadelivery.integracao.maps;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do acesso a Google Maps Platform (Routes e Geocoding).
 *
 * A chave pertence a aplicacao e vem do ambiente (GOOGLE_MAPS_API_KEY),
 * nunca do banco nem do repositorio.
 */
@ConfigurationProperties(prefix = "agiliza.maps")
public record GoogleMapsProperties(String apiKey, String routesUrl, String geocodingUrl, Duration timeout) {

    public static final String ROUTES_URL_PADRAO = "https://routes.googleapis.com/directions/v2:computeRoutes";
    public static final String GEOCODING_URL_PADRAO = "https://maps.googleapis.com/maps/api/geocode/json";

    public GoogleMapsProperties {
        if (routesUrl == null || routesUrl.isBlank()) {
            routesUrl = ROUTES_URL_PADRAO;
        }
        if (geocodingUrl == null || geocodingUrl.isBlank()) {
            geocodingUrl = GEOCODING_URL_PADRAO;
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(5);
        }
    }

    public boolean possuiChave() {
        return apiKey != null && !apiKey.isBlank();
    }
}
