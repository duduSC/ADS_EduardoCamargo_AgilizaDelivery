package br.com.agilizadelivery.integracao.maps;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

class GoogleGeocodingServicoDeGeocodificacaoTest {

    private static final String URL = "https://geocode.test/json";

    private MockRestServiceServer servidor;
    private GoogleGeocodingServicoDeGeocodificacao servico;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        servico = new GoogleGeocodingServicoDeGeocodificacao(
                new GoogleMapsProperties("chave-teste", null, URL, null, null, null), builder);
    }

    @Test
    @DisplayName("Deve geocodificar o endereço do cliente")
    void deveGeocodificar() {
        servidor.expect(requestTo(startsWith(URL)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("key", "chave-teste"))
                .andExpect(queryParam("region", "br"))
                .andRespond(withSuccess("""
                        {"status":"OK","results":[{"geometry":{"location":{"lat":-23.5614,"lng":-46.6559}}}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<Coordenada> coordenada = servico.geocodificar("Av. Paulista, 1000 - São Paulo");

        assertTrue(coordenada.isPresent());
        assertEquals(0, reais("-23.5614").compareTo(coordenada.get().getLatitude()));
        assertEquals(0, reais("-46.6559").compareTo(coordenada.get().getLongitude()));
        servidor.verify();
    }

    @Test
    @DisplayName("Deve devolver vazio quando o endereço não é encontrado ou a API falha")
    void deveDevolverVazioEmFalha() {
        servidor.expect(requestTo(startsWith(URL)))
                .andRespond(withSuccess("{\"status\":\"ZERO_RESULTS\",\"results\":[]}", MediaType.APPLICATION_JSON));
        assertTrue(servico.geocodificar("Endereço inexistente").isEmpty());

        servidor.reset();
        servidor.expect(requestTo(startsWith(URL))).andRespond(withServerError());
        assertTrue(servico.geocodificar("Av. Paulista, 1000").isEmpty());
    }

    @Test
    @DisplayName("Não deve chamar a API sem chave ou com endereço vazio")
    void naoDeveChamarSemChaveOuEndereco() {
        assertTrue(servico.geocodificar(" ").isEmpty());
        servidor.verify();

        GoogleGeocodingServicoDeGeocodificacao semChave = new GoogleGeocodingServicoDeGeocodificacao(
                new GoogleMapsProperties(null, null, URL, null, null, null), RestClient.builder());
        assertTrue(semChave.geocodificar("Av. Paulista, 1000").isEmpty());
    }
}
