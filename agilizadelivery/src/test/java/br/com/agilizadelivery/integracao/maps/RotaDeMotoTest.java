package br.com.agilizadelivery.integracao.maps;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

/**
 * Rota de moto na Routes API (D013, item 7; T015, item 3), contra um
 * servidor HTTP falso de verdade, que recebe o corpo pela rede.
 *
 * O modo padrao e TWO_WHEELER. Sem rota de moto para o trecho (resposta
 * sem rota, ou 400 de modo indisponivel), tenta uma vez com DRIVE.
 */
@ExtendWith(OutputCaptureExtension.class)
class RotaDeMotoTest {

    private static final String CHAVE = "chave-secreta-de-teste";

    /** Loja da demo (Av. Brasil, Centro, Passo Fundo) e um cliente no bairro. */
    private final Coordenada loja = new Coordenada(reais("-28.2620000"), reais("-52.4070000"));
    private final Coordenada cliente = new Coordenada(reais("-28.2512345"), reais("-52.3987654"));

    private final List<Chamada> chamadas = new CopyOnWriteArrayList<>();
    private HttpServer servidor;

    record Chamada(String corpo, String chave, String mascara) {
        String modo() {
            return JsonPath.read(corpo, "$.travelMode");
        }
    }

    record Resposta(int status, String corpo) {
    }

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    @DisplayName("Padrão TWO_WHEELER: o corpo enviado leva o modo de moto, as coordenadas e a unidade")
    void corpoComModoDeMoto(CapturedOutput saida) throws IOException {
        GoogleRoutesServicoDeRotas servico = servico(null, chamada -> rota(3456));

        assertThat(servico.distanciaKm(loja, cliente)).contains(reais("3.46"));

        assertThat(chamadas).hasSize(1);
        Chamada chamada = chamadas.getFirst();
        assertThat(chamada.modo()).isEqualTo("TWO_WHEELER");
        assertThat(chamada.chave()).isEqualTo(CHAVE);
        assertThat(chamada.mascara()).isEqualTo(GoogleRoutesServicoDeRotas.FIELD_MASK);
        Map<String, Object> corpo = JsonPath.read(chamada.corpo(), "$");
        assertThat(corpo).containsOnlyKeys("origin", "destination", "travelMode", "units");
        assertThat(JsonPath.<Double>read(chamada.corpo(), "$.origin.location.latLng.latitude")).isEqualTo(-28.262);
        assertThat(JsonPath.<Double>read(chamada.corpo(), "$.origin.location.latLng.longitude")).isEqualTo(-52.407);
        assertThat(JsonPath.<Double>read(chamada.corpo(), "$.destination.location.latLng.latitude"))
                .isEqualTo(-28.2512345);
        assertThat(JsonPath.<Double>read(chamada.corpo(), "$.destination.location.latLng.longitude"))
                .isEqualTo(-52.3987654);
        assertThat(JsonPath.<String>read(chamada.corpo(), "$.units")).isEqualTo("METRIC");
        assertThat(saida.getAll()).contains("TWO_WHEELER");
    }

    @Test
    @DisplayName("Resposta sem rota de moto: uma tentativa com DRIVE, e o km é o dela")
    void reservaSemRota(CapturedOutput saida) throws IOException {
        GoogleRoutesServicoDeRotas servico = servico(null,
                chamada -> chamada.modo().equals("DRIVE") ? rota(5100) : new Resposta(200, "{}"));

        assertThat(servico.distanciaKm(loja, cliente)).contains(reais("5.10"));

        assertThat(chamadas).extracting(Chamada::modo).containsExactly("TWO_WHEELER", "DRIVE");
        assertThat(chamadas.get(1).corpo()).isEqualTo(chamadas.get(0).corpo().replace("TWO_WHEELER", "DRIVE"));
        assertThat(saida.getAll()).contains("DRIVE");
    }

    @Test
    @DisplayName("Rota de moto com lista vazia ou sem distância também cai na reserva")
    void reservaComRotaVazia() throws IOException {
        List<String> semRota = new CopyOnWriteArrayList<>(List.of("{\"routes\":[]}", "{\"routes\":[{}]}"));
        GoogleRoutesServicoDeRotas servico = servico(null,
                chamada -> chamada.modo().equals("DRIVE") ? rota(1000) : new Resposta(200, semRota.removeFirst()));

        assertThat(servico.distanciaKm(loja, cliente)).contains(reais("1.00"));
        assertThat(servico.distanciaKm(loja, cliente)).contains(reais("1.00"));
        assertThat(chamadas).extracting(Chamada::modo)
                .containsExactly("TWO_WHEELER", "DRIVE", "TWO_WHEELER", "DRIVE");
    }

    @Test
    @DisplayName("400 da Routes API para o modo de moto: uma tentativa com DRIVE")
    void reservaEm400() throws IOException {
        GoogleRoutesServicoDeRotas servico = servico(null, chamada -> chamada.modo().equals("DRIVE")
                ? rota(2000)
                : new Resposta(400, "{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\"}}"));

        assertThat(servico.distanciaKm(loja, cliente)).contains(reais("2.00"));
        assertThat(chamadas).extracting(Chamada::modo).containsExactly("TWO_WHEELER", "DRIVE");
    }

    @Test
    @DisplayName("Sem rota nem de moto nem de carro: vazio, com exatamente duas chamadas")
    void semRotaNenhuma() throws IOException {
        GoogleRoutesServicoDeRotas servico = servico(null, chamada -> new Resposta(200, "{}"));

        assertThat(servico.distanciaKm(loja, cliente)).isEmpty();
        assertThat(chamadas).extracting(Chamada::modo).containsExactly("TWO_WHEELER", "DRIVE");
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = { 401, 403, 429, 500, 503 })
    @DisplayName("Chave recusada, cota ou API fora do ar: sem reserva, que falharia igual")
    void semReservaEmFalhaDaApi(int status) throws IOException {
        GoogleRoutesServicoDeRotas servico = servico(null, chamada -> new Resposta(status, "{}"));

        assertThat(servico.distanciaKm(loja, cliente)).isEmpty();
        assertThat(chamadas).hasSize(1);
    }

    @Test
    @DisplayName("Timeout: sem reserva, para não dobrar a espera")
    void semReservaEmTimeout() throws IOException {
        GoogleRoutesServicoDeRotas servico = servico(null, chamada -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return rota(1000);
        });

        assertThat(servico.distanciaKm(loja, cliente)).isEmpty();
        assertThat(chamadas).hasSize(1);
    }

    @Test
    @DisplayName("Configurado DRIVE: envia DRIVE e não repete a chamada")
    void modoConfiguradoDrive() throws IOException {
        GoogleRoutesServicoDeRotas servico = servico("DRIVE", chamada -> new Resposta(200, "{}"));

        assertThat(servico.distanciaKm(loja, cliente)).isEmpty();
        assertThat(chamadas).extracting(Chamada::modo).containsExactly("DRIVE");
    }

    @Test
    @DisplayName("O modo é normalizado e um valor desconhecido impede a subida")
    void modoValidado() {
        assertThat(propriedades(" two_wheeler ").modoViagem()).isEqualTo("TWO_WHEELER");
        assertThat(propriedades(null).modoViagem()).isEqualTo("TWO_WHEELER");
        assertThat(propriedades("").modoViagem()).isEqualTo("TWO_WHEELER");
        assertThat(propriedades("drive").modoViagem()).isEqualTo("DRIVE");
        assertThatThrownBy(() -> propriedades("WALK"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agiliza.maps.modo-viagem");
    }

    @Test
    @DisplayName("O log diz o modo usado, sem coordenadas do cliente nem chave")
    void logSemDadosDoCliente(CapturedOutput saida) throws IOException {
        GoogleRoutesServicoDeRotas servico = servico(null,
                chamada -> chamada.modo().equals("DRIVE") ? rota(5100) : new Resposta(400, "{\"error\":{}}"));

        servico.distanciaKm(loja, cliente);

        assertThat(saida.getAll())
                .contains("TWO_WHEELER", "DRIVE")
                .doesNotContain("-28.2512345", "-52.3987654", "28.25123", "52.39876", CHAVE);
    }

    private GoogleRoutesServicoDeRotas servico(String modo, Function<Chamada, Resposta> responder)
            throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/computeRoutes", troca -> {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Chamada chamada = new Chamada(corpo, troca.getRequestHeaders().getFirst("X-Goog-Api-Key"),
                    troca.getRequestHeaders().getFirst("X-Goog-FieldMask"));
            chamadas.add(chamada);
            Resposta resposta = responder.apply(chamada);
            byte[] bytes = resposta.corpo().getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            try {
                troca.sendResponseHeaders(resposta.status(), bytes.length);
                try (OutputStream saida = troca.getResponseBody()) {
                    saida.write(bytes);
                }
            } catch (IOException clienteDesistiu) {
                // O cliente encerrou a conexao (timeout): nada a responder.
            }
        });
        servidor.start();
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/computeRoutes";
        GoogleMapsProperties propriedades = new GoogleMapsProperties(
                CHAVE, url, null, Duration.ofSeconds(1), null, modo);
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(propriedades.timeout());
        fabrica.setReadTimeout(propriedades.timeout());
        return new GoogleRoutesServicoDeRotas(propriedades, RestClient.builder().requestFactory(fabrica));
    }

    private static GoogleMapsProperties propriedades(String modo) {
        return new GoogleMapsProperties(CHAVE, null, null, null, null, modo);
    }

    private static Resposta rota(long metros) {
        return new Resposta(200, "{\"routes\":[{\"distanceMeters\":" + metros + "}]}");
    }
}
