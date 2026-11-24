package br.com.agilizadelivery.integracao.maps;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do acesso a Google Maps Platform (Routes e Geocoding).
 *
 * cacheTamanho limita quantos enderecos e distancias ficam em memoria
 * para evitar consultas repetidas (risco R06).
 *
 * A chave pertence a aplicacao e vem do ambiente (GOOGLE_MAPS_API_KEY),
 * nunca do banco nem do repositorio.
 *
 * modoViagem e o travelMode da Routes API: TWO_WHEELER (moto, padrao,
 * D013) ou DRIVE (carro). Outro valor impede a subida da aplicacao.
 */
@ConfigurationProperties(prefix = "agiliza.maps")
public record GoogleMapsProperties(
        String apiKey,
        String routesUrl,
        String geocodingUrl,
        Duration timeout,
        Integer cacheTamanho,
        String modoViagem) {

    public static final String ROUTES_URL_PADRAO = "https://routes.googleapis.com/directions/v2:computeRoutes";
    public static final String GEOCODING_URL_PADRAO = "https://maps.googleapis.com/maps/api/geocode/json";
    public static final String MODO_MOTO = "TWO_WHEELER";
    public static final String MODO_CARRO = "DRIVE";
    private static final Set<String> MODOS_ACEITOS = Set.of(MODO_MOTO, MODO_CARRO);

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
        if (cacheTamanho == null || cacheTamanho < 1) {
            cacheTamanho = 1000;
        }
        modoViagem = modoViagem == null || modoViagem.isBlank()
                ? MODO_MOTO
                : modoViagem.trim().toUpperCase(Locale.ROOT);
        if (!MODOS_ACEITOS.contains(modoViagem)) {
            throw new IllegalArgumentException(
                    "agiliza.maps.modo-viagem aceita TWO_WHEELER ou DRIVE, e nao '" + modoViagem + "'.");
        }
    }

    public boolean possuiChave() {
        return apiKey != null && !apiKey.isBlank();
    }
}
