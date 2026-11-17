package br.com.agilizadelivery.service.rota;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

/** Fallback de distancia sem chave do Google (PROVISORIO, D009). */
class DistanciaEmLinhaRetaTest {

    private final DistanciaEmLinhaReta servico = new DistanciaEmLinhaReta();

    @Test
    @DisplayName("1 grau de longitude no equador: 111,19 km em linha reta x 1,3 = 144,55 km")
    void parConhecidoNoEquador() {
        assertThat(servico.distanciaKm(coordenada("0", "0"), coordenada("0", "1")))
                .contains(new BigDecimal("144.55"));
    }

    @Test
    @DisplayName("Mesma coordenada: distancia zero")
    void mesmaCoordenada() {
        assertThat(servico.distanciaKm(coordenada("-28.262", "-52.4083"), coordenada("-28.262", "-52.4083")))
                .contains(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Passo Fundo: loja demo ate a primeira entrega demo")
    void parEmPassoFundo() {
        // Haversine ~0,9775 km x 1,3 = 1,27 km
        assertThat(servico.distanciaKm(coordenada("-28.2620", "-52.4083"), coordenada("-28.2560", "-52.4010")))
                .contains(new BigDecimal("1.27"));
    }

    @Test
    @DisplayName("Coordenada ausente: sem distancia")
    void coordenadaAusente() {
        assertThat(servico.distanciaKm(null, coordenada("0", "0"))).isEmpty();
        assertThat(servico.distanciaKm(coordenada("0", "0"), new Coordenada())).isEmpty();
    }

    private static Coordenada coordenada(String lat, String lng) {
        return new Coordenada(new BigDecimal(lat), new BigDecimal(lng));
    }
}
