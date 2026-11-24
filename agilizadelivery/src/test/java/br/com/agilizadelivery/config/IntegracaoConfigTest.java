package br.com.agilizadelivery.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.agilizadelivery.integracao.maps.GoogleMapsProperties;
import br.com.agilizadelivery.integracao.maps.GoogleRoutesServicoDeRotas;
import br.com.agilizadelivery.service.rota.DistanciaEmLinhaReta;

/** Escolha da implementacao de rotas conforme a chave (D009). */
class IntegracaoConfigTest {

    @Test
    @DisplayName("Com chave configurada, usa a Google Routes API e nao o fallback")
    void comChaveUsaGoogle() {
        assertThat(IntegracaoConfig.rotasConforme(propriedades("chave-ficticia")))
                .isInstanceOf(GoogleRoutesServicoDeRotas.class);
    }

    @Test
    @DisplayName("Sem chave, usa a distancia em linha reta")
    void semChaveUsaLinhaReta() {
        assertThat(IntegracaoConfig.rotasConforme(propriedades(""))).isInstanceOf(DistanciaEmLinhaReta.class);
        assertThat(IntegracaoConfig.rotasConforme(propriedades(null))).isInstanceOf(DistanciaEmLinhaReta.class);
    }

    private static GoogleMapsProperties propriedades(String chave) {
        return new GoogleMapsProperties(chave, null, null, Duration.ofSeconds(1), 10, null);
    }
}
