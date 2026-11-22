package br.com.agilizadelivery.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;
import br.com.agilizadelivery.service.CanalAcesso;

/**
 * Ataques ao /ws com WebSocket cru, como faria um cliente malicioso
 * (Constituicao 5.6, 6.1 e 6.5): nada de biblioteca STOMP que limite os
 * frames que podem ser enviados.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:websocket;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "agiliza.cors.origens-permitidas=http://localhost:5173" })
@ExtendWith(OutputCaptureExtension.class)
class WebSocketSegurancaTest {

    private static final String ORIGEM_DO_FRONT = "http://localhost:5173";

    @Value("${local.server.port}")
    private int porta;

    @Autowired
    private ServicoJwt servicoJwt;

    @Autowired
    private SimpMessagingTemplate mensageiro;

    @Autowired
    @Qualifier("stompWebSocketHandlerMapping")
    private HandlerMapping mapeamento;

    private final List<ClienteCru> clientes = new ArrayList<>();
    private HttpClient http;
    private UUID lojaA;
    private UUID lojaB;

    @BeforeEach
    void setUp() {
        http = HttpClient.newHttpClient();
        lojaA = UUID.randomUUID();
        lojaB = UUID.randomUUID();
    }

    @AfterEach
    void fechar() {
        clientes.forEach(ClienteCru::abortar);
        http.close();
    }

    @Test
    @DisplayName("Origem do front e cliente sem Origin abrem o /ws")
    void origemPermitida() throws Exception {
        assertThat(abrir(ORIGEM_DO_FRONT)).isNotNull();
        assertThat(abrir(null)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://malicioso.example", "http://localhost:5174", "null" })
    @DisplayName("Origem fora da lista do CORS é recusada no handshake")
    void origemRecusada(String origem) {
        assertThatThrownBy(() -> abrir(origem))
                .hasCauseInstanceOf(WebSocketHandshakeException.class)
                .cause()
                .satisfies(erro -> assertThat(((WebSocketHandshakeException) erro).getResponse().statusCode())
                        .isEqualTo(403));
    }

    @Test
    @DisplayName("O endpoint usa a lista do CORS, sem curinga e sem depender do filtro de CORS")
    void endpointSemCuringa() {
        WebSocketHttpRequestHandler endpoint =
                (WebSocketHttpRequestHandler) ((SimpleUrlHandlerMapping) mapeamento).getHandlerMap().get("/ws");
        OriginHandshakeInterceptor origem = endpoint.getHandshakeInterceptors().stream()
                .filter(OriginHandshakeInterceptor.class::isInstance)
                .map(OriginHandshakeInterceptor.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(origem.getAllowedOrigins()).containsExactly(ORIGEM_DO_FRONT);
        assertThat(origem.getAllowedOriginPatterns()).isEmpty();
    }

    @Test
    @DisplayName("Batimento do cliente e UNSUBSCRIBE não derrubam a sessão")
    void sessaoNormal() throws Exception {
        ClienteCru cliente = conectado(operador(lojaA));
        String topico = topico(lojaA, "entregas");

        cliente.enviar("\n");
        cliente.assinar(topico);
        assertThat(primeiroQuadroPublicandoEm(cliente, topico)).startsWith("MESSAGE");
        cliente.enviar("UNSUBSCRIBE\nid:0\n\n\0");
        cliente.esvaziar();
        cliente.assinar(topico);

        assertThat(primeiroQuadroPublicandoEm(cliente, topico)).startsWith("MESSAGE");
    }

    @ParameterizedTest(name = "{0} com {1}")
    @CsvSource({ "CONNECT,nada", "CONNECT,lixo", "CONNECT,refresh", "STOMP,nada", "STOMP,lixo", "STOMP,refresh" })
    @DisplayName("CONNECT e o alias STOMP exigem token de acesso")
    void conexaoSemTokenValido(String comando, String credencial) throws Exception {
        String authorization = switch (credencial) {
            case "lixo" -> "Bearer token-invalido";
            case "refresh" -> "Bearer " + servicoJwt.gerarRefreshToken(UUID.randomUUID(), CanalAcesso.BACKOFFICE);
            default -> null;
        };
        ClienteCru cliente = abrir(null);

        cliente.conectar(comando, authorization);

        assertThat(cliente.proximo()).startsWith("ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = { "CONNECT", "STOMP" })
    @DisplayName("Operador recebe os dois tópicos da própria loja")
    void topicosDaPropriaLoja(String comando) throws Exception {
        ClienteCru cliente = abrir(ORIGEM_DO_FRONT);
        cliente.conectar(comando, "Bearer " + operador(lojaA));
        assertThat(cliente.proximo()).startsWith("CONNECTED");

        for (String sufixo : List.of("entregas", "frota")) {
            String topico = topico(lojaA, sufixo);
            cliente.assinar(topico);
            assertThat(primeiroQuadroPublicandoEm(cliente, topico)).startsWith("MESSAGE").contains(topico);
            cliente.esvaziar();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/topic/**",
            "/topic/estabelecimento/*/entregas",
            "/topic/estabelecimento/**",
            "/topic/estabelecimento/{id}/entregas",
            "/topic/estabelecimento/B/entregas",
            "/topic/estabelecimento/B/frota",
            "/topic/estabelecimento/A/../B/entregas",
            "/topic/estabelecimento/A/**",
            "/topic/estabelecimento/A/integracao",
            "/topic/estabelecimento/A",
            "/queue/qualquer",
            "/user/queue/qualquer" })
    @DisplayName("Padrões, outra loja e tópicos desconhecidos não podem ser assinados")
    void inscricaoProibida(String modelo) throws Exception {
        ClienteCru cliente = conectado(operador(lojaA));

        cliente.assinar(modelo.replace("/A", "/" + lojaA).replace("/B", "/" + lojaB));

        assertThat(primeiroQuadroPublicandoEm(cliente, topico(lojaB, "entregas"))).startsWith("ERROR");
    }

    @Test
    @DisplayName("Destino duplicado: o broker usa o mesmo destino que foi conferido")
    void destinoDuplicado() throws Exception {
        ClienteCru cliente = conectado(operador(lojaA));

        cliente.enviar("SUBSCRIBE\nid:0\ndestination:" + topico(lojaA, "entregas")
                + "\ndestination:/topic/**\n\n\0");

        for (int i = 0; i < 5; i++) {
            mensageiro.convertAndSend(topico(lojaB, "entregas"), "evento-da-loja-b");
            String quadro = cliente.proximo(200);
            assertThat(quadro == null || !quadro.contains("evento-da-loja-b")).isTrue();
        }
    }

    @Test
    @DisplayName("Frames emendados na mesma mensagem são conferidos um a um")
    void framesEmendados() throws Exception {
        ClienteCru cliente = conectado(operador(lojaA));

        cliente.enviar("SUBSCRIBE\nid:0\ndestination:" + topico(lojaA, "entregas") + "\n\n\0"
                + "SUBSCRIBE\nid:1\ndestination:/topic/**\n\n\0");

        assertThat(cliente.proximo()).startsWith("ERROR");
    }

    @Test
    @DisplayName("SUBSCRIBE sem CONNECT é recusado")
    void inscricaoSemConexao() throws Exception {
        ClienteCru cliente = abrir(null);

        cliente.assinar(topico(lojaA, "entregas"));

        assertThat(cliente.proximo()).startsWith("ERROR");
    }

    @ParameterizedTest
    @CsvSource({ "ENTREGADOR,APP", "ENTREGADOR,BACKOFFICE", "OPERADOR,APP", "ADMIN_GERAL,BACKOFFICE" })
    @DisplayName("Só o backoffice da loja assina os tópicos do painel")
    void perfilOuCanalSemPermissao(PerfilUsuario perfil, CanalAcesso canal) throws Exception {
        ClienteCru cliente = conectado(token(perfil, canal, perfil == PerfilUsuario.ADMIN_GERAL ? null : lojaA));

        cliente.assinar(topico(lojaA, "entregas"));

        assertThat(primeiroQuadroPublicandoEm(cliente, topico(lojaA, "entregas"))).startsWith("ERROR");
    }

    @ParameterizedTest(name = "{0} para a loja {1}")
    @CsvSource({ "SEND,A", "SEND,B", "MESSAGE,A", "MESSAGE,B" })
    @DisplayName("Cliente não publica eventos no broker, nem na própria loja")
    void publicacaoPeloCliente(String comando, String loja) throws Exception {
        UUID alvo = loja.equals("A") ? lojaA : lojaB;
        String topico = topico(alvo, "entregas");
        ClienteCru vitima = conectado(operador(alvo));
        vitima.assinar(topico);
        assertThat(primeiroQuadroPublicandoEm(vitima, topico)).startsWith("MESSAGE");
        vitima.esvaziar();

        ClienteCru atacante = conectado(operador(lojaA));
        String marca = "evento-forjado-" + UUID.randomUUID();
        String cabecalhos = "MESSAGE".equals(comando) ? "message-id:1\nsubscription:0\n" : "";
        atacante.enviar(comando + "\n" + cabecalhos + "destination:" + topico + "\n\n" + marca + "\0");

        String recebido;
        while ((recebido = vitima.proximo(1_500)) != null) {
            assertThat(recebido).doesNotContain(marca);
        }
    }

    @Test
    @DisplayName("Recusa não expõe classe interna nem registra o token")
    void recusaSemVazamento(CapturedOutput saida) throws Exception {
        String token = servicoJwt.gerarRefreshToken(UUID.randomUUID(), CanalAcesso.BACKOFFICE);
        ClienteCru cliente = abrir(null);

        cliente.conectar("CONNECT", "Bearer " + token);

        assertThat(cliente.proximo())
                .startsWith("ERROR")
                .doesNotContain("Executor", "Exception", "clientInboundChannel", "org.springframework", token);
        assertThat(saida.getAll()).doesNotContain(token);
    }

    private static String topico(UUID loja, String sufixo) {
        return "/topic/estabelecimento/" + loja + "/" + sufixo;
    }

    private String operador(UUID loja) {
        return token(PerfilUsuario.OPERADOR, CanalAcesso.BACKOFFICE, loja);
    }

    private String token(PerfilUsuario perfil, CanalAcesso canal, UUID loja) {
        return servicoJwt.gerarTokenAcesso(
                new UsuarioAutenticadoResponse(UUID.randomUUID(), loja, null, "Teste", perfil), canal);
    }

    private ClienteCru abrir(String origem) throws Exception {
        ClienteCru cliente = new ClienteCru();
        WebSocket.Builder construtor = http.newWebSocketBuilder();
        if (origem != null) {
            construtor.header("Origin", origem);
        }
        cliente.socket = construtor.buildAsync(URI.create("ws://localhost:" + porta + "/ws"), cliente)
                .get(5, TimeUnit.SECONDS);
        clientes.add(cliente);
        return cliente;
    }

    private ClienteCru conectado(String token) throws Exception {
        ClienteCru cliente = abrir(null);
        cliente.conectar("CONNECT", "Bearer " + token);
        assertThat(cliente.proximo()).startsWith("CONNECTED");
        return cliente;
    }

    /** Publica como o servidor ate o cliente receber algo (inscricao valida) ou ser recusado. */
    private String primeiroQuadroPublicandoEm(ClienteCru cliente, String topico) throws Exception {
        for (int tentativa = 0; tentativa < 15; tentativa++) {
            String quadro = cliente.proximo(200);
            if (quadro != null) {
                return quadro;
            }
            mensageiro.convertAndSend(topico, "evento-do-servidor");
        }
        return cliente.proximo(500);
    }

    private static final class ClienteCru implements WebSocket.Listener {

        private final LinkedBlockingQueue<String> quadros = new LinkedBlockingQueue<>();
        private final StringBuilder parcial = new StringBuilder();
        private WebSocket socket;
        private int inscricoes;

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence dados, boolean ultimo) {
            parcial.append(dados);
            if (ultimo) {
                if (!parcial.toString().isBlank()) {
                    quadros.add(parcial.toString());
                }
                parcial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        void conectar(String comando, String authorization) throws Exception {
            enviar(comando + "\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n"
                    + (authorization == null ? "" : "Authorization:" + authorization + "\n") + "\n\0");
        }

        void assinar(String destino) throws Exception {
            enviar("SUBSCRIBE\nid:" + inscricoes++ + "\ndestination:" + destino + "\n\n\0");
        }

        void enviar(String quadro) throws Exception {
            socket.sendText(quadro, true).get(5, TimeUnit.SECONDS);
        }

        String proximo() throws InterruptedException {
            return proximo(5_000);
        }

        String proximo(long esperaMs) throws InterruptedException {
            return quadros.poll(esperaMs, TimeUnit.MILLISECONDS);
        }

        void esvaziar() throws InterruptedException {
            while (proximo(300) != null) {
                // descarta as publicacoes repetidas da espera pela inscricao
            }
        }

        void abortar() {
            socket.abort();
        }
    }
}
