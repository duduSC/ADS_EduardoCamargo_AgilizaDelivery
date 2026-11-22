package br.com.agilizadelivery.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;
import br.com.agilizadelivery.service.CanalAcesso;
import tools.jackson.databind.ObjectMapper;

/**
 * Criterio de aceite da T014: nenhuma rota devolve erro fora do
 * ApiErrorResponse (Constituicao 6.4).
 *
 * Servidor de verdade, e nao MockMvc, porque o firewall do Spring Security,
 * o filtro de CORS e o proprio Tomcat respondem por fora do controller.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:erros;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "agiliza.cors.origens-permitidas=http://localhost:5173" })
class ContratoDeErrosTest {

    private static final String UM_ID = "00000000-0000-4000-8000-000000000001";
    private static final int UM_MEGA = 1024 * 1024;

    private enum Quem {
        NINGUEM, OPERADOR, ADMIN_LOJA, ADMIN_GERAL, ENTREGADOR, TOKEN_LIXO, REFRESH, OPERADOR_CANAL_APP,
        ENTREGADOR_CANAL_BACKOFFICE
    }

    private record Caso(String nome, String metodo, String caminho, Quem quem, Map<String, String> cabecalhos,
            String corpo, boolean semTamanho, int status, String codigo) {

        Caso(String nome, String metodo, String caminho, Quem quem, String corpo, int status, String codigo) {
            this(nome, metodo, caminho, quem,
                    corpo == null ? Map.of() : Map.of("Content-Type", "application/json"),
                    corpo, false, status, codigo);
        }

        @Override
        public String toString() {
            return nome;
        }
    }

    @Value("${local.server.port}")
    private int porta;

    @Autowired
    private ServicoJwt servicoJwt;

    @Autowired
    private ObjectMapper json;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    }

    @AfterEach
    void fechar() {
        http.close();
    }

    static Stream<Caso> casos() {
        String grande = "{\"observacoes\":\"" + "a".repeat(UM_MEGA + 200_000) + "\"}";
        Map<String, String> ehJson = Map.of("Content-Type", "application/json");
        return Stream.of(
                // rota e caminho
                new Caso("rota inexistente", "GET", "/api/nao-existe", Quem.OPERADOR, null,
                        404, "RECURSO_NAO_ENCONTRADO"),
                new Caso("recurso estatico inexistente", "GET", "/favicon.ico", Quem.OPERADOR, null,
                        404, "RECURSO_NAO_ENCONTRADO"),
                new Caso("barra no fim", "GET", "/api/entregas/", Quem.OPERADOR, null,
                        404, "RECURSO_NAO_ENCONTRADO"),
                new Caso("acesso direto a /error", "GET", "/error", Quem.OPERADOR, null,
                        404, "RECURSO_NAO_ENCONTRADO"),
                new Caso("barra dupla", "POST", "/api/caixas//reabrir", Quem.ADMIN_LOJA, "{\"motivo\":\"x\"}",
                        400, "PARAMETRO_INVALIDO"),
                new Caso("barra dupla sem token", "GET", "/api//entregas", Quem.NINGUEM, null,
                        400, "PARAMETRO_INVALIDO"),
                new Caso("ponto e virgula no caminho", "GET", "/api/entregas;x=1", Quem.OPERADOR, null,
                        400, "PARAMETRO_INVALIDO"),
                new Caso("variavel de caminho vazia", "GET", "/api/entregas/%20", Quem.OPERADOR, null,
                        400, "PARAMETRO_INVALIDO"),
                new Caso("UUID invalido no caminho", "GET", "/api/entregas/abc", Quem.OPERADOR, null,
                        400, "PARAMETRO_INVALIDO"),

                // protocolo
                new Caso("metodo errado", "DELETE", "/api/entregas", Quem.OPERADOR, null,
                        405, "RECURSO_NAO_ENCONTRADO"),
                new Caso("metodo errado no login", "GET", "/api/entregador/lotes/" + UM_ID + "/iniciar",
                        Quem.ENTREGADOR, null, 405, "RECURSO_NAO_ENCONTRADO"),
                new Caso("Accept sem JSON", "GET", "/api/entregas", Quem.OPERADOR,
                        Map.of("Accept", "application/xml"), null, false, 406, "PARAMETRO_INVALIDO"),
                new Caso("Accept sem JSON em erro", "GET", "/api/nao-existe", Quem.OPERADOR,
                        Map.of("Accept", "text/html"), null, false, 404, "RECURSO_NAO_ENCONTRADO"),
                new Caso("Accept sem JSON em 401", "GET", "/api/entregas", Quem.NINGUEM,
                        Map.of("Accept", "text/html"), null, false, 401, "NAO_AUTENTICADO"),
                new Caso("Content-Type errado", "POST", "/api/entregas", Quem.OPERADOR,
                        Map.of("Content-Type", "text/plain"), "oi", false, 415, "CORPO_REQUISICAO_INVALIDO"),

                // corpo e parametros
                new Caso("JSON malformado", "POST", "/api/entregas", Quem.OPERADOR, "{\"nomeCliente\":",
                        400, "CORPO_REQUISICAO_INVALIDO"),
                new Caso("JSON malformado no login", "POST", "/api/auth/login", Quem.NINGUEM, "{",
                        400, "CORPO_REQUISICAO_INVALIDO"),
                new Caso("corpo vazio", "POST", "/api/recibos", Quem.OPERADOR, "",
                        400, "CORPO_REQUISICAO_INVALIDO"),
                new Caso("tipo errado no corpo", "POST", "/api/lotes", Quem.OPERADOR, "{\"motoboyId\":\"abc\"}",
                        400, "CORPO_REQUISICAO_INVALIDO"),
                new Caso("campo invalido", "POST", "/api/caixas", Quem.OPERADOR, "{}",
                        400, "DADOS_INVALIDOS"),
                new Caso("item invalido em lista", "PUT", "/api/faixas-preco", Quem.ADMIN_LOJA,
                        "[{\"deKm\":0,\"ateKm\":3,\"valorPago\":-1,\"vigenteDesde\":\"2026-11-22\"}]",
                        400, "DADOS_INVALIDOS"),
                new Caso("parametro ausente", "GET", "/api/acertos", Quem.OPERADOR, null,
                        400, "PARAMETRO_INVALIDO"),
                new Caso("enum errado na consulta", "GET", "/api/entregas?status=INVENTADO", Quem.OPERADOR, null,
                        400, "PARAMETRO_INVALIDO"),
                new Caso("data errada na consulta", "GET", "/api/caixas?dataInicio=ontem", Quem.OPERADOR, null,
                        400, "PARAMETRO_INVALIDO"),
                new Caso("id numerico errado", "POST", "/api/falhas-integracao/abc/descartar", Quem.ADMIN_LOJA, null,
                        400, "PARAMETRO_INVALIDO"),
                new Caso("corpo grande demais", "POST", "/api/entregas", Quem.OPERADOR, ehJson, grande, false,
                        413, "CORPO_REQUISICAO_INVALIDO"),
                new Caso("corpo grande demais sem tamanho", "POST", "/api/entregas", Quem.OPERADOR, ehJson, grande,
                        true, 413, "CORPO_REQUISICAO_INVALIDO"),
                new Caso("corpo grande demais no login", "POST", "/api/auth/login", Quem.NINGUEM, ehJson, grande,
                        false, 413, "CORPO_REQUISICAO_INVALIDO"),

                // autenticacao, canal e perfil
                new Caso("sem token", "GET", "/api/entregas", Quem.NINGUEM, null, 401, "NAO_AUTENTICADO"),
                new Caso("sem token em rota inexistente", "GET", "/api/nao-existe", Quem.NINGUEM, null,
                        401, "NAO_AUTENTICADO"),
                new Caso("token invalido", "GET", "/api/entregas", Quem.TOKEN_LIXO, null, 401, "NAO_AUTENTICADO"),
                new Caso("refresh no lugar do acesso", "GET", "/api/entregas", Quem.REFRESH, null,
                        401, "NAO_AUTENTICADO"),
                new Caso("token do app no backoffice", "GET", "/api/entregas", Quem.ENTREGADOR, null,
                        403, "ACESSO_NEGADO"),
                new Caso("token do backoffice no app", "GET", "/api/entregador/me", Quem.OPERADOR, null,
                        403, "ACESSO_NEGADO"),
                new Caso("perfil do backoffice com canal APP", "GET", "/api/entregas", Quem.OPERADOR_CANAL_APP, null,
                        403, "ACESSO_NEGADO"),
                new Caso("entregador com canal BACKOFFICE", "GET", "/api/entregador/me",
                        Quem.ENTREGADOR_CANAL_BACKOFFICE, null, 403, "ACESSO_NEGADO"),
                new Caso("operador em rota da loja", "GET", "/api/usuarios", Quem.OPERADOR, null,
                        403, "ACESSO_NEGADO"),
                new Caso("admin geral em rota de operacao", "GET", "/api/entregas", Quem.ADMIN_GERAL, null,
                        403, "ACESSO_NEGADO"),
                new Caso("loja em rota da plataforma", "GET", "/api/estabelecimentos", Quem.ADMIN_LOJA, null,
                        403, "ACESSO_NEGADO"),
                new Caso("actuator fora do health", "GET", "/actuator/env", Quem.OPERADOR, null,
                        403, "ACESSO_NEGADO"),
                new Caso("origem fora do CORS", "GET", "/api/entregas", Quem.OPERADOR,
                        Map.of("Origin", "https://malicioso.example"), null, false, 403, "ACESSO_NEGADO"),
                new Caso("preflight de origem fora do CORS", "OPTIONS", "/api/entregas", Quem.NINGUEM,
                        Map.of("Origin", "https://malicioso.example", "Access-Control-Request-Method", "GET"),
                        null, false, 403, "ACESSO_NEGADO"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    @DisplayName("Toda resposta de erro vem no ApiErrorResponse")
    void erroNoFormatoPadrao(Caso caso) throws Exception {
        HttpResponse<String> resposta = chamar(caso);

        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(caso.status());
        assertThat(resposta.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        conferirFormato(resposta.body(), caso.status(), caso.codigo(), URI.create(caso.caminho()).getRawPath());
    }

    @Test
    @DisplayName("405 informa os métodos aceitos no cabeçalho Allow")
    void metodoErradoInformaAllow() throws Exception {
        HttpResponse<String> resposta = chamar(new Caso("405", "DELETE", "/api/entregas", Quem.OPERADOR, null,
                405, "RECURSO_NAO_ENCONTRADO"));

        assertThat(resposta.headers().firstValue("Allow").orElse("")).contains("GET", "POST");
    }

    @Test
    @DisplayName("CORS libera a origem do front também numa resposta de erro")
    void origemDoFrontLeOErro() throws Exception {
        HttpResponse<String> resposta = chamar(new Caso("cors", "GET", "/api/nao-existe", Quem.OPERADOR,
                Map.of("Origin", "http://localhost:5173"), null, false, 404, "RECURSO_NAO_ENCONTRADO"));

        assertThat(resposta.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:5173");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/entregas/%2F", "/api/entregas?x={", "/api/entregas/%zz", "/../../etc/passwd" })
    @DisplayName("Requisição recusada pelo Tomcat também sai no formato padrão")
    void recusadoPeloContainer(String alvo) throws Exception {
        String resposta;
        try (Socket socket = new Socket("localhost", porta)) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write(("GET " + alvo + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            resposta = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(resposta).startsWith("HTTP/1.1 400").contains("application/json")
                .contains("\"codigo\":\"PARAMETRO_INVALIDO\"")
                .doesNotContainIgnoringCase("<html", "tomcat", "exception");
    }

    @Test
    @DisplayName("Health é público e não mostra componentes")
    void healthSemDetalhes() throws Exception {
        HttpResponse<String> resposta = chamar(new Caso("health", "GET", "/actuator/health", Quem.NINGUEM, null,
                200, null));

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("\"status\":\"UP\"")
                .doesNotContain("components", "details", "database", "H2");
    }

    private void conferirFormato(String corpo, int status, String codigo, String caminho) {
        Map<?, ?> erro = json.readValue(corpo, LinkedHashMap.class);
        List<String> chaves = erro.keySet().stream().map(String::valueOf).toList();
        assertThat(chaves)
                .contains("timestamp", "status", "error", "codigo", "message", "path")
                .isSubsetOf("timestamp", "status", "error", "codigo", "message", "path", "campos");
        assertThat(erro.get("status")).isEqualTo(status);
        assertThat(erro.get("codigo")).isEqualTo(codigo);
        assertThat(erro.get("path")).isEqualTo(caminho);
        assertThat(String.valueOf(erro.get("message"))).isNotBlank().isNotEqualTo("No message available");
        assertThat(corpo).doesNotContain("org.springframework", "org.hibernate", "tools.jackson", "java.lang",
                "br.com.agilizadelivery", "Exception", "\"trace\"", "insert into", "select ");
    }

    private HttpResponse<String> chamar(Caso caso) throws Exception {
        HttpRequest.BodyPublisher corpo = caso.corpo() == null
                ? BodyPublishers.noBody()
                : BodyPublishers.ofString(caso.corpo());
        UnaryOperator<HttpRequest.BodyPublisher> semTamanho = BodyPublishers::fromPublisher;
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caso.caminho()))
                .method(caso.metodo(), caso.semTamanho() ? semTamanho.apply(corpo) : corpo);
        caso.cabecalhos().forEach(requisicao::header);
        String token = token(caso.quem());
        if (token != null) {
            requisicao.header("Authorization", "Bearer " + token);
        }
        return http.send(requisicao.build(), BodyHandlers.ofString());
    }

    private String token(Quem quem) {
        return switch (quem) {
            case NINGUEM -> null;
            case TOKEN_LIXO -> "nao-e-um-jwt";
            case REFRESH -> servicoJwt.gerarRefreshToken(UUID.randomUUID(), CanalAcesso.BACKOFFICE);
            case OPERADOR -> acesso(PerfilUsuario.OPERADOR, CanalAcesso.BACKOFFICE);
            case ADMIN_LOJA -> acesso(PerfilUsuario.ADMIN_RESTAURANTE, CanalAcesso.BACKOFFICE);
            case ADMIN_GERAL -> acesso(PerfilUsuario.ADMIN_GERAL, CanalAcesso.BACKOFFICE);
            case ENTREGADOR -> acesso(PerfilUsuario.ENTREGADOR, CanalAcesso.APP);
            case OPERADOR_CANAL_APP -> acesso(PerfilUsuario.OPERADOR, CanalAcesso.APP);
            case ENTREGADOR_CANAL_BACKOFFICE -> acesso(PerfilUsuario.ENTREGADOR, CanalAcesso.BACKOFFICE);
        };
    }

    private String acesso(PerfilUsuario perfil, CanalAcesso canal) {
        UUID loja = perfil == PerfilUsuario.ADMIN_GERAL ? null : UUID.randomUUID();
        return servicoJwt.gerarTokenAcesso(
                new UsuarioAutenticadoResponse(UUID.randomUUID(), loja, null, "Teste", perfil), canal);
    }
}
