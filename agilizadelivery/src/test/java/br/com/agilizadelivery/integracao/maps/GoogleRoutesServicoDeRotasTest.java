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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

class GoogleRoutesServicoDeRotasTest {

    private static final String URL = "https://routes.test/computeRoutes";

    private final Coordenada loja = new Coordenada(reais("-23.5505200"), reais("-46.6333080"));
    private final Coordenada cliente = new Coordenada(reais("-23.5614000"), reais("-46.6559000"));

    private MockRestServiceServer servidor;
    private GoogleRoutesServicoDeRotas servico;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        servico = new GoogleRoutesServicoDeRotas(
                new GoogleMapsProperties("chave-teste", URL, null, Duration.ofSeconds(1), null), builder);
    }

    @Test
    @DisplayName("Deve consultar a rota viária loja -> cliente e converter metros em km")
    void deveCalcularDistancia() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "chave-teste"))
                .andExpect(header("X-Goog-FieldMask", GoogleRoutesServicoDeRotas.FIELD_MASK))
                .andExpect(jsonPath("$.travelMode").value("DRIVE"))
                .andExpect(jsonPath("$.origin.location.latLng.latitude").value(-23.55052))
                .andExpect(jsonPath("$.destination.location.latLng.longitude").value(-46.6559))
                .andExpect(jsonPath("$.intermediates").doesNotExist())
                .andRespond(withSuccess("{\"routes\":[{\"distanceMeters\":2104}]}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.of(reais("2.10")), servico.distanciaKm(loja, cliente));
        servidor.verify();
    }

    @Test
    @DisplayName("Deve devolver vazio em erro HTTP ou resposta sem rota")
    void deveDevolverVazioEmFalha() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());
        assertTrue(servico.distanciaKm(loja, cliente).isEmpty());

        servidor.reset();
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertTrue(servico.distanciaKm(loja, cliente).isEmpty());
    }

    @Test
    @DisplayName("Não deve chamar a API sem chave ou sem coordenadas completas")
    void naoDeveChamarSemChaveOuCoordenada() {
        assertTrue(servico.distanciaKm(loja, null).isEmpty());
        assertTrue(servico.distanciaKm(loja, new Coordenada(reais("-23.5"), null)).isEmpty());
        servidor.verify();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer semChamadas = MockRestServiceServer.bindTo(builder).build();
        GoogleRoutesServicoDeRotas semChave = new GoogleRoutesServicoDeRotas(
                new GoogleMapsProperties(" ", URL, null, null, null), builder);
        assertTrue(semChave.distanciaKm(loja, cliente).isEmpty());
        semChamadas.verify();
    }
}
