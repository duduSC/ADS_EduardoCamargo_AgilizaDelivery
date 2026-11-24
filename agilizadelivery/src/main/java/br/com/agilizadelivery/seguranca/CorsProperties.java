package br.com.agilizadelivery.seguranca;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origens que podem chamar a API pelo navegador (Constituicao 6.5).
 * Lista vazia: nenhuma origem cruzada e aceita. "*" e recusado na subida.
 */
@ConfigurationProperties(prefix = "agiliza.cors")
public record CorsProperties(List<String> origensPermitidas) {

    public CorsProperties {
        origensPermitidas = origensPermitidas == null
                ? List.of()
                : origensPermitidas.stream().map(String::trim).filter(origem -> !origem.isEmpty()).toList();
        if (origensPermitidas.stream().anyMatch(origem -> origem.contains("*"))) {
            throw new IllegalStateException("agiliza.cors.origens-permitidas nao aceita curinga (*).");
        }
    }
}
