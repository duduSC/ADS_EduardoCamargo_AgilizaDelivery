package br.com.agilizadelivery.tempo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import br.com.agilizadelivery.controller.CenarioWeb;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.repository.EntregaStatusHistoricoRepository;

/**
 * Horario do negocio com a JVM em UTC, como no container (T014).
 *
 * O contrato com os clientes e "LocalDateTime local, sem fuso", no horario
 * de Sao Paulo. O relogio do sistema fica congelado num instante em que o
 * dia de Sao Paulo e o dia UTC podem ser diferentes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FusoHorarioTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final String VINTE_E_UMA_E_TRINTA = "2026-11-22T21:30:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioWeb cenario;

    @Autowired
    private EntregaStatusHistoricoRepository historicoRepository;

    private Estabelecimento loja;
    private Motoboy motoboy;
    private Usuario administrador;
    private String bearerOperador;
    private String bearerAdministrador;
    private String bearerEntregador;
    private MockedStatic<Clock> relogio;

    @BeforeEach
    void setUp() {
        assertThat(ZoneId.systemDefault().getRules().getOffset(Instant.now()))
                .as("estes testes rodam com -Duser.timezone=UTC (pom.xml), como o container")
                .isEqualTo(ZoneOffset.UTC);
        loja = cenario.estabelecimento();
        administrador = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);
        motoboy = cenario.motoboy(loja);
        bearerOperador = cenario.bearer(cenario.usuario(loja, PerfilUsuario.OPERADOR));
        bearerAdministrador = cenario.bearer(administrador);
        bearerEntregador = cenario.bearer(motoboy.getUsuario());
    }

    @AfterEach
    void descongelar() {
        if (relogio != null) {
            relogio.close();
        }
    }

    /** Congela o instante do sistema; o fuso continua sendo o que cada chamada pedir. */
    private void congelarEm(LocalDateTime emSaoPaulo) {
        Instant instante = emSaoPaulo.atZone(SAO_PAULO).toInstant();
        relogio = mockStatic(Clock.class, chamada -> switch (chamada.getMethod().getName()) {
            case "systemDefaultZone" -> Clock.fixed(instante, ZoneId.systemDefault());
            case "system" -> Clock.fixed(instante, chamada.getArgument(0));
            default -> chamada.callRealMethod();
        });
    }

    @ParameterizedTest(name = "as {0} de Sao Paulo")
    @ValueSource(strings = { VINTE_E_UMA_E_TRINTA, "2026-11-22T23:59:59", "2026-11-22T10:15:20",
            "2026-12-31T22:00:05" })
    @DisplayName("Finalizar, acerto, contestar, recibo e fechar o caixa caem no dia de São Paulo")
    void fluxoDoFimDoDia(String horario) throws Exception {
        String dia = LocalDateTime.parse(horario).toLocalDate().toString();
        congelarEm(LocalDateTime.parse(horario));

        String caixaId = id(operador(post("/api/caixas"), "{\"dataReferencia\":\"" + dia + "\"}")
                .andExpect(status().isCreated()));
        String entregaId = entregaEmRota();

        entregador(post("/api/entregador/entregas/" + entregaId + "/status"),
                "{\"status\":\"ENTREGUE\",\"ocorridoEm\":\"" + horario + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalizadaEm", is(horario)));

        entregador(get("/api/entregador/acerto"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataReferenciaCaixa", is(dia)))
                .andExpect(jsonPath("$.contestacaoPermitida", is(true)))
                .andExpect(jsonPath("$.extrato.linhas", hasSize(1)))
                .andExpect(jsonPath("$.extrato.linhas[0].entregaId", is(entregaId)));

        entregador(post("/api/entregador/entregas/" + entregaId + "/contestar"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendenteRevisao", is(true)));
        operador(post("/api/entregas/" + entregaId + "/revisao/resolver"),
                "{\"valorRepasse\":18.50,\"motivo\":\"Contestacao procedente\"}")
                .andExpect(status().isOk());
        operador(get("/api/entregas/" + entregaId + "/ajustes"), null)
                .andExpect(jsonPath("$[0].ajustadoEm", is(horario)));

        String reciboId = id(operador(post("/api/recibos"), """
                {"motoboyId":"%s","periodoInicio":"%sT00:00:00","periodoFim":"%sT23:59:59"}
                """.formatted(motoboy.getId(), dia, dia))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataReferencia", is(dia)))
                .andExpect(jsonPath("$.criadoEm", is(horario))));
        operador(get("/api/recibos"), null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(reciboId)));
        operador(post("/api/recibos/" + reciboId + "/pagar"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagoEm", is(horario)));

        operador(get("/api/caixas/atual"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(caixaId)));
        operador(post("/api/caixas/" + caixaId + "/fechar"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FECHADO")))
                .andExpect(jsonPath("$.fechadoEm", is(horario)));
        entregador(get("/api/entregador/acerto"), null)
                .andExpect(jsonPath("$.statusCaixa", is("FECHADO")))
                .andExpect(jsonPath("$.contestacaoPermitida", is(false)));
        administrador(post("/api/caixas/" + caixaId + "/reabrir"), "{\"motivo\":\"Divergencia\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reabertoEm", is(horario)));
    }

    @Test
    @DisplayName("GET /api/caixas/atual e o histórico usam o hoje de São Paulo às 21:30")
    void caixaAtual() throws Exception {
        congelarEm(LocalDateTime.parse(VINTE_E_UMA_E_TRINTA));
        operador(post("/api/caixas"), "{\"dataReferencia\":\"2026-11-22\"}").andExpect(status().isCreated());

        operador(get("/api/caixas/atual"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataReferencia", is("2026-11-22")));
        operador(get("/api/caixas"), null).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Acerto sem período, às 21:30, traz a entrega recém-finalizada")
    void acertoSemPeriodo() throws Exception {
        congelarEm(LocalDateTime.parse(VINTE_E_UMA_E_TRINTA));
        String entregaId = entregaEmRota();
        entregador(post("/api/entregador/entregas/" + entregaId + "/status"),
                "{\"status\":\"ENTREGUE\",\"ocorridoEm\":\"" + VINTE_E_UMA_E_TRINTA + "\"}")
                .andExpect(status().isOk());

        entregador(get("/api/entregador/acerto"), null)
                .andExpect(jsonPath("$.extrato.linhas", hasSize(1)));
    }

    @Test
    @DisplayName("Todo horário gravado pelo servidor sai no horário de São Paulo, sem fuso")
    void horariosDoServidor() throws Exception {
        String agora = VINTE_E_UMA_E_TRINTA;
        congelarEm(LocalDateTime.parse(agora));

        String entregaId = id(operador(post("/api/entregas"), entregaAvulsa())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criadoEm", is(agora))));
        operador(post("/api/entregas/" + entregaId + "/pronto"), null)
                .andExpect(jsonPath("$.prontoParaDespachoEm", is(agora)));
        entregador(patch("/api/entregador/posicao"), "{\"latitude\":-23.56,\"longitude\":-46.65}")
                .andExpect(jsonPath("$.ultimaPosicaoEm", is(agora)));
        operador(post("/api/caixas"), "{\"dataReferencia\":\"2026-11-22\"}")
                .andExpect(jsonPath("$.abertoEm", is(agora)));

        String loteId = id(operador(post("/api/lotes"), despacho(entregaId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.despachadoEm", is(agora))));
        operador(get("/api/entregas/" + entregaId), null)
                .andExpect(jsonPath("$.despachadaEm", is(agora)));
        entregador(post("/api/entregador/lotes/" + loteId + "/iniciar"), null).andExpect(status().isOk());
        operador(post("/api/entregas/" + entregaId + "/cancelar"), "{\"motivo\":\"Cliente desistiu\"}")
                .andExpect(jsonPath("$.finalizadaEm", is(agora)));
        operador(get("/api/lotes/" + loteId), null)
                .andExpect(jsonPath("$.concluidoEm", is(agora)));

        assertThat(historicoRepository.findByEntregaEstabelecimentoIdAndEntregaIdOrderByRegistradoEmAsc(
                loja.getId(), UUID.fromString(entregaId)))
                .hasSize(4)
                .allSatisfy(evento -> assertThat(evento.getRegistradoEm()).isEqualTo(LocalDateTime.parse(agora)));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginEmail\":\"%s\",\"senha\":\"%s\"}"
                        .formatted(administrador.getLoginEmail(), CenarioWeb.SENHA)))
                .andExpect(status().isOk());
        administrador(get("/api/usuarios"), null)
                .andExpect(jsonPath("$[?(@.id=='" + administrador.getId() + "')].ultimoAcessoEm",
                        is(List.of(agora))));
    }

    @Test
    @DisplayName("Nenhum código de produção lê o relógio sem fuso")
    void nenhumRelogioSemFuso() throws IOException {
        Pattern semFuso = Pattern.compile(
                "\\b(LocalDateTime|LocalDate|LocalTime)\\.now\\(\\)|systemDefault(Zone)?\\(\\)");
        try (Stream<Path> fontes = Files.walk(Path.of("src/main/java"))) {
            List<String> ocorrencias = fontes.filter(fonte -> fonte.toString().endsWith(".java"))
                    .flatMap(fonte -> linhas(fonte).stream()
                            .filter(linha -> semFuso.matcher(linha).find())
                            .map(linha -> fonte.getFileName() + ": " + linha.trim()))
                    .toList();
            assertThat(ocorrencias).isEmpty();
        }
    }

    private static List<String> linhas(Path fonte) {
        try {
            return Files.readAllLines(fonte);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String entregaEmRota() throws Exception {
        String entregaId = id(operador(post("/api/entregas"), entregaAvulsa()).andExpect(status().isCreated()));
        String loteId = id(operador(post("/api/lotes"), despacho(entregaId)).andExpect(status().isCreated()));
        entregador(post("/api/entregador/lotes/" + loteId + "/iniciar"), null).andExpect(status().isOk());
        return entregaId;
    }

    private static String entregaAvulsa() {
        return """
                {"nomeCliente":"Cliente","enderecoCompleto":"Av. Paulista, 1000",
                 "coordenada":{"latitude":-23.5614,"longitude":-46.6559}}
                """;
    }

    private String despacho(String entregaId) {
        return "{\"motoboyId\":\"%s\",\"entregaIds\":[\"%s\"]}".formatted(motoboy.getId(), entregaId);
    }

    private static String id(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions operador(MockHttpServletRequestBuilder requisicao, String corpo) throws Exception {
        return executar(requisicao, bearerOperador, corpo);
    }

    private ResultActions administrador(MockHttpServletRequestBuilder requisicao, String corpo) throws Exception {
        return executar(requisicao, bearerAdministrador, corpo);
    }

    private ResultActions entregador(MockHttpServletRequestBuilder requisicao, String corpo) throws Exception {
        return executar(requisicao, bearerEntregador, corpo);
    }

    private ResultActions executar(MockHttpServletRequestBuilder requisicao, String bearer, String corpo)
            throws Exception {
        requisicao.header(HttpHeaders.AUTHORIZATION, bearer);
        if (corpo != null) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mockMvc.perform(requisicao);
    }
}
