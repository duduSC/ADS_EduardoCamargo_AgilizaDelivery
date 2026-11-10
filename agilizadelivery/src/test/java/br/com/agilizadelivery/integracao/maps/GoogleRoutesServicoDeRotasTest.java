package br.com.agilizadelivery.integracao.maps;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.service.rota.PontoRota;
import br.com.agilizadelivery.service.rota.RotaCalculada;

class GoogleRoutesServicoDeRotasTest {

    private static final String URL = "https://routes.test/computeRoutes";

    private final PontoRota loja = ponto("-23.5505200", "-46.6333080");
    private final PontoRota b = ponto("-23.5614000", "-46.6559000");
    private final PontoRota c = new PontoRota(null, "Rua Augusta, 500 - São Paulo");
    private final PontoRota a = ponto("-23.5700000", "-46.6400000");

    private MockRestServiceServer servidor;
    private GoogleRoutesServicoDeRotas servico;

    private static PontoRota ponto(String latitude, String longitude) {
        return new PontoRota(new Coordenada(reais(latitude), reais(longitude)), "endereço");
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        servico = new GoogleRoutesServicoDeRotas(
                new GoogleMapsProperties("chave-teste", URL, null, Duration.ofSeconds(1)), builder);
    }

    @Test
    @DisplayName("Deve enviar as paradas na ordem do operador (B, C, A) e converter metros em km")
    void deveCalcularRotaComParadasNaOrdem() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "chave-teste"))
                .andExpect(header("X-Goog-FieldMask", GoogleRoutesServicoDeRotas.FIELD_MASK))
                .andExpect(jsonPath("$.travelMode").value("DRIVE"))
                .andExpect(jsonPath("$.origin.location.latLng.latitude").value(-23.55052))
                .andExpect(jsonPath("$.intermediates.length()").value(2))
                .andExpect(jsonPath("$.intermediates[0].location.latLng.latitude").value(-23.5614))
                .andExpect(jsonPath("$.intermediates[1].address").value("Rua Augusta, 500 - São Paulo"))
                .andExpect(jsonPath("$.destination.location.latLng.latitude").value(-23.57))
                .andRespond(withSuccess("""
                        {"routes":[{"distanceMeters":8450,
                          "legs":[{"distanceMeters":2100},{"distanceMeters":3350},{"distanceMeters":3000}]}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<RotaCalculada> rota = servico.calcularRota(loja, List.of(b, c, a));

        assertTrue(rota.isPresent());
        assertEquals(reais("8.45"), rota.get().distanciaTotalKm());
        assertEquals(List.of(reais("2.10"), reais("3.35"), reais("3.00")), rota.get().trechosKm());
        servidor.verify();
    }

    @Test
    @DisplayName("Deve calcular a distância loja -> cliente sem paradas intermediárias")
    void deveCalcularDistanciaSimples() {
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.intermediates").doesNotExist())
                .andRespond(withSuccess("{\"routes\":[{\"distanceMeters\":2104}]}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.of(reais("2.10")), servico.distanciaKm(loja, b));
    }

    @Test
    @DisplayName("Deve devolver vazio em erro HTTP ou resposta sem rota")
    void deveDevolverVazioEmFalha() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());
        assertTrue(servico.distanciaKm(loja, b).isEmpty());

        servidor.reset();
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertTrue(servico.distanciaKm(loja, b).isEmpty());
    }

    @Test
    @DisplayName("Não deve chamar a API sem chave configurada")
    void naoDeveChamarSemChave() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer semChamadas = MockRestServiceServer.bindTo(builder).build();
        GoogleRoutesServicoDeRotas semChave = new GoogleRoutesServicoDeRotas(
                new GoogleMapsProperties(" ", URL, null, null), builder);

        assertTrue(semChave.distanciaKm(loja, b).isEmpty());
        semChamadas.verify();
    }

    @Test
    @DisplayName("Não deve chamar a API com ponto sem coordenada nem endereço")
    void naoDeveChamarComPontoInvalido() {
        assertTrue(servico.distanciaKm(loja, new PontoRota(null, " ")).isEmpty());
        assertTrue(servico.calcularRota(loja, List.of()).isEmpty());
        servidor.verify();
    }
}
