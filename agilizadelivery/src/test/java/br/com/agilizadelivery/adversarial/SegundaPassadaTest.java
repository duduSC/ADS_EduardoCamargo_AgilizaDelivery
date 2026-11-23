package br.com.agilizadelivery.adversarial;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.agilizadelivery.controller.CenarioWeb;
import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.integracao.pedidos.CicloIntegracao;
import br.com.agilizadelivery.integracao.pedidos.IntegracaoPedidosProperties;
import br.com.agilizadelivery.integracao.pedidos.PedidoRecebido;
import br.com.agilizadelivery.integracao.pedidos.ProvedorDePedidos;
import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.repository.FalhaIntegracaoRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.service.support.ImportacaoPedido;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Segunda passada da T014: "como eu quebraria isto?". Payloads extremos,
 * protocolo torto, token forjado, trava presa e importacao simultanea.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:adversarial;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;"
                + "LOCK_TIMEOUT=800",
        "agiliza.cors.origens-permitidas=http://localhost:5173" })
class SegundaPassadaTest {

    private enum Quem {
        NINGUEM, OPERADOR, ADMIN, ENTREGADOR, FORJADO_SEM_ASSINATURA, CABECALHO_ANINHADO, CABECALHO_EXPOENTE
    }

    private record Golpe(String nome, Quem quem, String metodo, String caminho, Map<String, String> cabecalhos,
            String corpo) {

        Golpe(String nome, Quem quem, String metodo, String caminho, String corpo) {
            this(nome, quem, metodo, caminho,
                    corpo == null ? Map.of() : Map.of("Content-Type", "application/json"), corpo);
        }

        @Override
        public String toString() {
            return nome;
        }
    }

    @Value("${local.server.port}")
    private int porta;
    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private EntregaRepository entregaRepository;
    @Autowired
    private LoteEntregaRepository loteRepository;
    @Autowired
    private EstabelecimentoRepository estabelecimentoRepository;
    @Autowired
    private ConfiguracaoIntegracaoRepository configuracaoRepository;
    @Autowired
    private FalhaIntegracaoRepository falhaRepository;
    @Autowired
    private ImportacaoPedido importacao;
    @Autowired
    private PlatformTransactionManager gerenciador;

    private HttpClient http;
    private ExecutorService executor;
    private Estabelecimento loja;
    private Usuario operador;
    private String tokenOperador;
    private String tokenAdmin;
    private String tokenEntregador;
    private Motoboy motoboy;
    private Entrega emRota;
    private Entrega pendente;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        executor = Executors.newFixedThreadPool(3);
        loja = cenario.estabelecimento();
        operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        tokenOperador = cenario.token(operador);
        tokenAdmin = cenario.token(cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE));
        motoboy = cenario.motoboy(loja);
        tokenEntregador = cenario.token(motoboy.getUsuario());
        LoteEntrega lote = new LoteEntrega();
        lote.setEstabelecimento(loja);
        lote.setMotoboy(motoboy);
        lote.setUsuarioDespachante(operador);
        lote.setStatus(StatusLote.EM_ROTA);
        lote.setQtdEntregas((short) 1);
        loteRepository.save(lote);
        emRota = cenario.entrega(loja, StatusEntrega.EM_ROTA);
        emRota.setLote(lote);
        entregaRepository.save(emRota);
        pendente = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
    }

    @AfterEach
    void fechar() {
        executor.shutdownNow();
        http.close();
    }

    static Stream<Golpe> golpes() {
        String fimDosTempos = "+999999999-12-31T23:59:59";
        String status = "/api/entregador/entregas/{emRota}/status";
        String avulsa = "{\"nomeCliente\":\"%s\",\"enderecoCompleto\":\"Rua A\",%s}";
        String coordenada = "\"coordenada\":{\"latitude\":%s,\"longitude\":%s}";
        List<Golpe> golpes = new ArrayList<>(List.of(
                new Golpe("ano 999999999 no ocorridoEm", Quem.ENTREGADOR, "POST", status,
                        "{\"status\":\"ENTREGUE\",\"ocorridoEm\":\"" + fimDosTempos + "\"}"),
                new Golpe("ano 999999999 no caixa", Quem.OPERADOR, "POST", "/api/caixas",
                        "{\"dataReferencia\":\"+999999999-12-31\"}"),
                new Golpe("ano 999999999 na faixa", Quem.ADMIN, "PUT", "/api/faixas-preco",
                        "[{\"deKm\":0,\"ateKm\":3,\"valorPago\":9,\"vigenteDesde\":\"+999999999-12-31\"}]"),
                new Golpe("ano 999999999 no recibo", Quem.OPERADOR, "POST", "/api/recibos",
                        "{\"motoboyId\":\"{motoboy}\",\"periodoInicio\":\"-999999999-01-01T00:00:00\","
                                + "\"periodoFim\":\"" + fimDosTempos + "\"}"),
                new Golpe("periodo infinito no extrato", Quem.OPERADOR, "GET",
                        "/api/acertos?motoboyId={motoboy}&inicio=-999999999-01-01T00:00:00&fim=%2B999999999-12-31T23:59:59",
                        null),
                new Golpe("periodo infinito nos caixas", Quem.OPERADOR, "GET",
                        "/api/caixas?dataInicio=-999999999-01-01&dataFim=%2B999999999-12-31", null),
                new Golpe("ultimo dia dos tempos no relatorio", Quem.OPERADOR, "GET",
                        "/api/relatorios/dia?data=%2B999999999-12-31", null),
                new Golpe("primeiro dia dos tempos no relatorio", Quem.OPERADOR, "GET",
                        "/api/relatorios/dia?data=-999999999-01-01", null),
                new Golpe("periodo infinito no acerto do app", Quem.ENTREGADOR, "GET",
                        "/api/entregador/acerto?inicio=%2B999999999-12-31T00:00:00&fim=%2B999999999-12-31T23:59:59",
                        null),

                new Golpe("antipoda", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("C", coordenada.formatted("23.5505200", "133.3666920"))),
                new Golpe("pino na antipoda", Quem.OPERADOR, "PATCH", "/api/entregas/{pendente}/coordenada",
                        "{\"latitude\":23.5505200,\"longitude\":133.3666920}"),
                new Golpe("polo", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("C", coordenada.formatted("90", "180"))),

                new Golpe("NUL no texto", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("a\\u0000b", coordenada.formatted("-23.56", "-46.65"))),
                new Golpe("surrogate solto", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("\\uD800", coordenada.formatted("-23.56", "-46.65"))),
                new Golpe("nome no limite com emoji", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("\\uD83C\\uDF55".repeat(120), coordenada.formatted("-23.56", "-46.65"))),
                new Golpe("observacao de 900 KB", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("C", "\"observacoes\":\"" + "x".repeat(900_000) + "\","
                                + coordenada.formatted("-23.56", "-46.65"))),

                new Golpe("corpo null", Quem.OPERADOR, "POST", "/api/entregas", "null"),
                new Golpe("lista com null", Quem.ADMIN, "PUT", "/api/faixas-preco", "[null]"),
                new Golpe("lista vazia", Quem.ADMIN, "PUT", "/api/faixas-preco", "[]"),
                new Golpe("objeto no lugar de lista", Quem.ADMIN, "PUT", "/api/faixas-preco", "{}"),
                new Golpe("ids com null", Quem.OPERADOR, "POST", "/api/lotes",
                        "{\"motoboyId\":\"{motoboy}\",\"entregaIds\":[null]}"),
                new Golpe("ids repetidos", Quem.OPERADOR, "POST", "/api/lotes",
                        "{\"motoboyId\":\"{motoboy}\",\"entregaIds\":[\"{pendente}\",\"{pendente}\"]}"),
                new Golpe("20 mil ids", Quem.OPERADOR, "POST", "/api/lotes",
                        "{\"motoboyId\":\"{motoboy}\",\"entregaIds\":["
                                + ("\"" + UUID.randomUUID() + "\",").repeat(20_000) + "\"{pendente}\"]}"),
                new Golpe("aninhamento de 100 mil niveis", Quem.OPERADOR, "POST", "/api/entregas",
                        "{\"x\":" + "[".repeat(100_000) + "]".repeat(100_000) + "}"),
                new Golpe("numero de 5 mil digitos", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("C", "\"valorPedido\":" + "9".repeat(5_000))),
                new Golpe("NaN", Quem.ENTREGADOR, "PATCH", "/api/entregador/posicao",
                        "{\"latitude\":\"NaN\",\"longitude\":\"Infinity\"}"),
                new Golpe("chave repetida", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("C", "\"nomeCliente\":\"D\"")),

                new Golpe("id gigante", Quem.ADMIN, "POST", "/api/falhas-integracao/99999999999999999999/descartar",
                        null),
                new Golpe("e-mail de 400 KB sem login", Quem.NINGUEM, "POST", "/api/auth/login",
                        "{\"loginEmail\":\"" + "a.".repeat(100_000) + "a@" + "b.".repeat(100_000)
                                + "com\",\"senha\":\"x\"}"),
                new Golpe("senha de 900 KB sem login", Quem.NINGUEM, "POST", "/api/auth/login",
                        "{\"loginEmail\":\"ninguem@teste.com\",\"senha\":\"" + "s".repeat(900_000) + "\"}"),
                new Golpe("CNPJ de 500 KB", Quem.ADMIN, "POST", "/api/estabelecimentos",
                        "{\"cnpj\":\"" + "1".repeat(500_000) + "\"}"),
                new Golpe("expoente em campo desconhecido", Quem.OPERADOR, "POST", "/api/caixas",
                        "{\"desconhecido\":1e-999999999}"),
                new Golpe("expoente em data", Quem.OPERADOR, "POST", "/api/caixas",
                        "{\"dataReferencia\":1e999999999}"),
                new Golpe("JWT com cabecalho aninhado", Quem.CABECALHO_ANINHADO, "GET", "/api/entregas", null),
                new Golpe("JWT com expoente no cabecalho", Quem.CABECALHO_EXPOENTE, "GET", "/api/entregas", null),
                new Golpe("id negativo", Quem.ADMIN, "POST", "/api/falhas-integracao/-1/descartar", null),
                new Golpe("escape no caminho", Quem.OPERADOR, "GET", "/api/entregador/%2e%2e/entregas", null),
                new Golpe("NUL no caminho", Quem.OPERADOR, "GET", "/api/entregas/%00", null),
                new Golpe("TRACE", Quem.NINGUEM, "TRACE", "/api/entregas", null),
                new Golpe("metodo inventado", Quem.OPERADOR, "PROPFIND", "/api/entregas", null),
                new Golpe("health por POST", Quem.NINGUEM, "POST", "/actuator/health", null),
                new Golpe("componente do health", Quem.OPERADOR, "GET", "/actuator/health/db", null),
                new Golpe("token sem assinatura", Quem.FORJADO_SEM_ASSINATURA, "GET", "/api/entregas", null),
                new Golpe("cabecalho de 20 KB", Quem.OPERADOR, "GET", "/api/entregas",
                        Map.of("X-Enche", "x".repeat(20_000)), null),
                new Golpe("/ws sem upgrade", Quem.NINGUEM, "GET", "/ws", null),
                new Golpe("/ws por POST", Quem.NINGUEM, "POST", "/ws", null),
                new Golpe("/ws/qualquer", Quem.OPERADOR, "GET", "/ws/qualquer", null)));
        return golpes.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("golpes")
    @DisplayName("Nada quebra: nunca 5xx, e todo erro no formato padrão")
    void nadaQuebra(Golpe golpe) throws Exception {
        HttpResponse<String> resposta = chamar(golpe);

        assertThat(resposta.statusCode()).as(abreviar(resposta.body())).isLessThan(500);
        if (resposta.statusCode() >= 400) {
            assertThat(resposta.headers().firstValue("Content-Type").orElse(""))
                    .as(abreviar(resposta.body())).startsWith("application/json");
            assertThat(resposta.body()).contains("\"codigo\":", "\"path\":")
                    .doesNotContain("Exception", "org.springframework", "org.hibernate", "\"trace\"");
        }
    }

    @Test
    @DisplayName("Preflight do front continua valendo para todos os métodos da API")
    void preflightDoFront() throws Exception {
        for (String metodo : List.of("GET", "POST", "PUT", "PATCH")) {
            HttpResponse<String> resposta = chamar(new Golpe("preflight", Quem.NINGUEM, "OPTIONS",
                    "/api/faixas-preco", Map.of("Origin", "http://localhost:5173",
                            "Access-Control-Request-Method", metodo,
                            "Access-Control-Request-Headers", "authorization,content-type"), null));

            assertThat(resposta.statusCode()).as(metodo).isEqualTo(200);
            assertThat(resposta.headers().firstValue("Access-Control-Allow-Origin"))
                    .contains("http://localhost:5173");
        }
    }

    @Test
    @DisplayName("Trava presa além do limite responde 409, não 500, e o recurso segue utilizável")
    void travaPresa() throws Exception {
        CountDownLatch travada = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        Future<?> segurando = executor.submit(() -> new TransactionTemplate(gerenciador).executeWithoutResult(s -> {
            estabelecimentoRepository.bloquearPorId(loja.getId());
            travada.countDown();
            try {
                soltar.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(travada.await(5, TimeUnit.SECONDS)).isTrue();
        Golpe pronto = new Golpe("pronto", Quem.OPERADOR, "POST", "/api/entregas/{pendente}/pronto", null);

        HttpResponse<String> resposta = chamar(pronto);
        soltar.countDown();
        segurando.get(10, TimeUnit.SECONDS);

        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(409);
        assertThat(resposta.body()).contains("\"codigo\":\"DADO_DUPLICADO\"");
        assertThat(chamar(pronto).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Dois ciclos do worker com o mesmo pedido: uma entrega e nenhuma falha (HU03)")
    void importacaoSimultanea() throws Exception {
        ConfiguracaoIntegracao configuracao = new ConfiguracaoIntegracao();
        configuracao.setEstabelecimento(loja);
        configuracao.setProvedor("SIMULADOR");
        configuracao.setMerchantId("adversarial-" + loja.getId());
        configuracao.setAtivo(true);
        configuracaoRepository.save(configuracao);
        PedidoRecebido pedido = new PedidoRecebido("adversarial-" + UUID.randomUUID(), "1", "PLACED", "Pedido unico",
                null, "Rua B, 20", null, null, null, null, loja.getCoordenada(), null, null, BigDecimal.TEN, null,
                true, null, null, List.of(), "{}");
        CountDownLatch largada = new CountDownLatch(1);
        ProvedorDePedidos provedor = new ProvedorDePedidos() {
            @Override
            public String provedor() {
                return "SIMULADOR";
            }

            @Override
            public List<PedidoRecebido> buscarNovosPedidos(ConfiguracaoIntegracao config) {
                return config.getId().equals(configuracao.getId()) ? List.of(pedido) : List.of();
            }

            @Override
            public void confirmarRecebimento(ConfiguracaoIntegracao config, String idExterno) {
                // o provedor de teste nao exige confirmacao
            }
        };
        CicloIntegracao ciclo = new CicloIntegracao(List.of(provedor), configuracaoRepository, falhaRepository,
                importacao, new IntegracaoPedidosProperties(
                        new IntegracaoPedidosProperties.Worker(false, Duration.ofSeconds(30), Duration.ofMillis(50), 5),
                        null));

        List<Future<?>> ciclos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ciclos.add(executor.submit(() -> {
                largada.await();
                ciclo.executar();
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> executado : ciclos) {
            executado.get(20, TimeUnit.SECONDS);
        }

        assertThat(entregaRepository.findByEstabelecimentoIdAndStatusOrderByCriadoEmAsc(
                loja.getId(), StatusEntrega.AGUARDANDO_DESPACHO))
                .filteredOn(entrega -> entrega.getNomeCliente().equals("Pedido unico"))
                .hasSize(1);
        assertThat(falhaRepository.findAll())
                .filteredOn(falha -> falha.getIdExterno() != null && falha.getIdExterno().equals(pedido.idExterno()))
                .isEmpty();
    }

    private HttpResponse<String> chamar(Golpe golpe) throws Exception {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + porta + preencher(golpe.caminho())))
                .timeout(Duration.ofSeconds(20))
                .method(golpe.metodo(), golpe.corpo() == null
                        ? BodyPublishers.noBody()
                        : BodyPublishers.ofString(preencher(golpe.corpo())));
        golpe.cabecalhos().forEach(requisicao::header);
        String token = switch (golpe.quem()) {
            case NINGUEM -> null;
            case OPERADOR -> tokenOperador;
            case ADMIN -> tokenAdmin;
            case ENTREGADOR -> tokenEntregador;
            case FORJADO_SEM_ASSINATURA -> semAssinatura();
            // o cabecalho do JWT e lido antes de conferir a assinatura
            case CABECALHO_ANINHADO -> comCabecalho("[".repeat(1_800) + "]".repeat(1_800));
            case CABECALHO_EXPOENTE -> comCabecalho("1e-999999999");
        };
        if (token != null) {
            requisicao.header("Authorization", "Bearer " + token);
        }
        return http.send(requisicao.build(), BodyHandlers.ofString());
    }

    private String semAssinatura() {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String corpo = ("{\"iss\":\"agilizadelivery\",\"sub\":\"%s\",\"estabelecimentoId\":\"%s\","
                + "\"perfil\":\"OPERADOR\",\"canal\":\"BACKOFFICE\",\"tipo\":\"ACESSO\",\"exp\":4102444800}")
                .formatted(operador.getId(), loja.getId());
        return base64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + base64.encodeToString(corpo.getBytes(StandardCharsets.UTF_8)) + ".";
    }

    private static String comCabecalho(String valorExtra) {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String cabecalho = "{\"alg\":\"HS256\",\"x\":" + valorExtra + "}";
        return base64.encodeToString(cabecalho.getBytes(StandardCharsets.UTF_8)) + "."
                + base64.encodeToString("{}".getBytes(StandardCharsets.UTF_8)) + "."
                + base64.encodeToString(new byte[32]);
    }

    private String preencher(String modelo) {
        return modelo.replace("{emRota}", emRota.getId().toString())
                .replace("{pendente}", pendente.getId().toString())
                .replace("{motoboy}", motoboy.getId().toString())
                .replace("{hoje}", HorarioLocal.hoje().toString());
    }

    private static String abreviar(String texto) {
        return texto.length() <= 300 ? texto : texto.substring(0, 300);
    }
}
