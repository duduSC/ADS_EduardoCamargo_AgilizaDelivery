package br.com.agilizadelivery.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import br.com.agilizadelivery.integracao.maps.GoogleGeocodingServicoDeGeocodificacao;
import br.com.agilizadelivery.integracao.maps.GoogleMapsProperties;
import br.com.agilizadelivery.integracao.maps.GoogleRoutesServicoDeRotas;
import br.com.agilizadelivery.service.rota.DistanciaEmLinhaReta;
import br.com.agilizadelivery.service.rota.GeocodificacaoComCache;
import br.com.agilizadelivery.service.rota.RotasComCache;
import br.com.agilizadelivery.service.rota.ServicoDeGeocodificacao;
import br.com.agilizadelivery.service.rota.ServicoDeRotas;
import lombok.extern.slf4j.Slf4j;

/**
 * Monta as portas de mapas: adapters da Google Maps Platform envolvidos
 * pelos decorators de cache (risco R06).
 *
 * Sem GOOGLE_MAPS_API_KEY, a distancia vem de DistanciaEmLinhaReta
 * (PROVISORIO, D009). A escolha acontece so aqui, na montagem do bean:
 * os services continuam dependendo apenas da porta ServicoDeRotas.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(GoogleMapsProperties.class)
public class IntegracaoConfig {

    @Bean
    public ServicoDeGeocodificacao servicoDeGeocodificacao(GoogleMapsProperties properties) {
        return new GeocodificacaoComCache(
                new GoogleGeocodingServicoDeGeocodificacao(properties, clienteHttp(properties)),
                properties.cacheTamanho());
    }

    @Bean
    public ServicoDeRotas servicoDeRotas(GoogleMapsProperties properties) {
        return new RotasComCache(rotasConforme(properties), properties.cacheTamanho());
    }

    /** Implementacao da porta de rotas conforme a presenca da chave. */
    static ServicoDeRotas rotasConforme(GoogleMapsProperties properties) {
        if (properties.possuiChave()) {
            return new GoogleRoutesServicoDeRotas(properties, clienteHttp(properties));
        }
        log.warn("GOOGLE_MAPS_API_KEY ausente: distância em linha reta (PROVISÓRIO, D009)");
        return new DistanciaEmLinhaReta();
    }

    private static RestClient.Builder clienteHttp(GoogleMapsProperties properties) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(properties.timeout());
        fabrica.setReadTimeout(properties.timeout());
        return RestClient.builder().requestFactory(fabrica);
    }
}
