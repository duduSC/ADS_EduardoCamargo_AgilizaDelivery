package br.com.agilizadelivery.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import br.com.agilizadelivery.integracao.maps.GoogleMapsProperties;

@Configuration
@EnableConfigurationProperties(GoogleMapsProperties.class)
public class IntegracaoConfig {
}
