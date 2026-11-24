package br.com.agilizadelivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.service.support.TransicaoEntrega;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Constituicao 5.5 e DVP 1.4.4: cada estado contra cada operacao que
 * existe na API. O que nao esta na tabela responde 4xx e nao muda nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MaquinasDeEstadoTest {

    private enum Operacao {
        DESPACHAR, INICIAR, DEVOLVER, CANCELAR, ENTREGAR, FALHAR,
        PEDIR_AGUARDANDO, PEDIR_DESPACHADA, PEDIR_EM_ROTA, PEDIR_CANCELADA
    }

    /** Transicoes do DVP 1.4.4, por quem as provoca. */
    private static final Map<StatusEntrega, Map<Operacao, StatusEntrega>> VALIDAS = Map.of(
            StatusEntrega.AGUARDANDO_DESPACHO, Map.of(
                    Operacao.DESPACHAR, StatusEntrega.DESPACHADA,
                    Operacao.CANCELAR, StatusEntrega.CANCELADA),
            StatusEntrega.DESPACHADA, Map.of(
                    Operacao.INICIAR, StatusEntrega.EM_ROTA,
                    Operacao.DEVOLVER, StatusEntrega.AGUARDANDO_DESPACHO,
                    Operacao.CANCELAR, StatusEntrega.CANCELADA),
            StatusEntrega.EM_ROTA, Map.of(
                    Operacao.ENTREGAR, StatusEntrega.ENTREGUE,
                    Operacao.FALHAR, StatusEntrega.FALHA,
                    Operacao.CANCELAR, StatusEntrega.CANCELADA),
            StatusEntrega.ENTREGUE, Map.of(),
            StatusEntrega.FALHA, Map.of(),
            StatusEntrega.CANCELADA, Map.of());

    private static final Map<StatusLote, Set<StatusLote>> LOTE = Map.of(
            StatusLote.DESPACHADO, Set.of(StatusLote.EM_ROTA, StatusLote.CANCELADO),
            StatusLote.EM_ROTA, Set.of(StatusLote.CONCLUIDO),
            StatusLote.CONCLUIDO, Set.of(),
            StatusLote.CANCELADO, Set.of());

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private EntregaRepository entregaRepository;
    @Autowired
    private LoteEntregaRepository loteRepository;
    @Autowired
    private TransicaoEntrega transicao;

    private Estabelecimento loja;
    private Usuario operador;
    private Motoboy motoboy;
    private String bearerOperador;
    private String bearerEntregador;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        motoboy = cenario.motoboy(loja);
        bearerOperador = cenario.bearer(operador);
        bearerEntregador = cenario.bearer(motoboy.getUsuario());
    }

    static Stream<Arguments> entregaPorOperacao() {
        return Stream.of(StatusEntrega.values())
                .flatMap(origem -> Stream.of(Operacao.values()).map(operacao -> Arguments.of(origem, operacao)));
    }

    @ParameterizedTest(name = "ENTREGA {0} + {1}")
    @MethodSource("entregaPorOperacao")
    @DisplayName("ENTREGA: só as transições da tabela")
    void entrega(StatusEntrega origem, Operacao operacao) throws Exception {
        LoteEntrega lote = switch (origem) {
            case AGUARDANDO_DESPACHO -> null;
            case DESPACHADA -> lote(StatusLote.DESPACHADO);
            case EM_ROTA -> lote(StatusLote.EM_ROTA);
            case ENTREGUE, FALHA, CANCELADA -> lote(StatusLote.CONCLUIDO);
        };
        Entrega entrega = cenario.entrega(loja, origem);
        entrega.setLote(lote);
        entregaRepository.saveAndFlush(entrega);
        // sem lote, iniciar e devolver miram um lote que nao e da entrega
        String loteId = (lote != null ? lote : lote(StatusLote.CANCELADO)).getId().toString();
        StatusEntrega esperado = VALIDAS.get(origem).get(operacao);

        MockHttpServletResponse resposta = executar(operacao, entrega.getId().toString(), loteId);

        if (esperado != null) {
            assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isBetween(200, 201);
            assertThat(entrega.getStatus()).isEqualTo(esperado);
        } else {
            assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isIn(404, 422);
            assertThat(entrega.getStatus()).isEqualTo(origem);
            if (resposta.getStatus() == 422) {
                assertThat(resposta.getContentAsString())
                        .containsAnyOf("TRANSICAO_ESTADO_INVALIDA", "ENTREGA_INAPTA_LOTE");
            }
        }
    }

    static Stream<Arguments> paresDeStatusDaEntrega() {
        return Stream.of(StatusEntrega.values())
                .flatMap(de -> Stream.of(StatusEntrega.values()).map(para -> Arguments.of(de, para)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("paresDeStatusDaEntrega")
    @DisplayName("TransicaoEntrega recusa todo par fora da tabela")
    void pontoUnicoDeTransicao(StatusEntrega de, StatusEntrega para) {
        Entrega entrega = cenario.entrega(loja, de);

        if (VALIDAS.get(de).containsValue(para)) {
            transicao.transitar(entrega, para, operador, null, null);
            assertThat(entrega.getStatus()).isEqualTo(para);
        } else {
            assertThatThrownBy(() -> transicao.transitar(entrega, para, operador, null, null))
                    .isInstanceOf(TransicaoEstadoInvalidaException.class);
            assertThat(entrega.getStatus()).isEqualTo(de);
        }
    }

    static Stream<Arguments> paresDeStatusDoLote() {
        return Stream.of(StatusLote.values())
                .flatMap(de -> Stream.of(StatusLote.values())
                        .filter(para -> para != StatusLote.DESPACHADO)
                        .map(para -> Arguments.of(de, para)));
    }

    @ParameterizedTest(name = "LOTE {0} -> {1}")
    @MethodSource("paresDeStatusDoLote")
    @DisplayName("LOTE: iniciar, concluir e devolver só a partir do estado certo")
    void lote(StatusLote de, StatusLote para) throws Exception {
        LoteEntrega lote = lote(de);
        Entrega entrega = cenario.entrega(loja, switch (de) {
            case DESPACHADO -> StatusEntrega.DESPACHADA;
            case EM_ROTA -> StatusEntrega.EM_ROTA;
            case CONCLUIDO -> StatusEntrega.ENTREGUE;
            case CANCELADO -> StatusEntrega.CANCELADA;
        });
        entrega.setLote(lote);
        entregaRepository.saveAndFlush(entrega);
        Operacao operacao = switch (para) {
            case EM_ROTA -> Operacao.INICIAR;
            case CONCLUIDO -> Operacao.ENTREGAR;
            case CANCELADO -> Operacao.DEVOLVER;
            case DESPACHADO -> throw new IllegalArgumentException("o lote so nasce DESPACHADO");
        };

        MockHttpServletResponse resposta = executar(operacao, entrega.getId().toString(), lote.getId().toString());

        if (LOTE.get(de).contains(para)) {
            assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isEqualTo(200);
            assertThat(lote.getStatus()).isEqualTo(para);
        } else {
            assertThat(resposta.getStatus()).isEqualTo(422);
            assertThat(resposta.getContentAsString()).contains("TRANSICAO_ESTADO_INVALIDA");
            assertThat(lote.getStatus()).isEqualTo(de);
        }
    }

    static Stream<Arguments> situacoesDoEntregador() {
        return Stream.of(StatusCadastroMotoboy.values())
                .flatMap(cadastro -> Stream.of(StatusDisponibilidadeMotoboy.values())
                        .map(disponibilidade -> Arguments.of(cadastro, disponibilidade)));
    }

    @ParameterizedTest(name = "despacho para {0}/{1}")
    @MethodSource("situacoesDoEntregador")
    @DisplayName("Despacho só para entregador ATIVO e ONLINE")
    void despachoSoParaAtivoOnline(StatusCadastroMotoboy cadastro, StatusDisponibilidadeMotoboy disponibilidade)
            throws Exception {
        motoboy = cenario.motoboy(loja, cadastro, disponibilidade);
        Entrega entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);

        MockHttpServletResponse resposta = executar(Operacao.DESPACHAR, entrega.getId().toString(), null);

        if (cadastro == StatusCadastroMotoboy.ATIVO && disponibilidade == StatusDisponibilidadeMotoboy.ONLINE) {
            assertThat(resposta.getStatus()).isEqualTo(201);
        } else {
            assertThat(resposta.getStatus()).isEqualTo(422);
            assertThat(resposta.getContentAsString()).contains("MOTOBOY_INDISPONIVEL");
            assertThat(entrega.getStatus()).isEqualTo(StatusEntrega.AGUARDANDO_DESPACHO);
        }
    }

    private MockHttpServletResponse executar(Operacao operacao, String entrega, String lote) throws Exception {
        MockHttpServletRequestBuilder requisicao = switch (operacao) {
            case DESPACHAR -> corpo(post("/api/lotes"),
                    "{\"motoboyId\":\"%s\",\"entregaIds\":[\"%s\"]}".formatted(motoboy.getId(), entrega));
            case INICIAR -> post("/api/entregador/lotes/" + lote + "/iniciar");
            case DEVOLVER -> post("/api/lotes/" + lote + "/devolver");
            case CANCELAR -> corpo(post("/api/entregas/" + entrega + "/cancelar"), "{\"motivo\":\"x\"}");
            case ENTREGAR -> mudarStatus(entrega, StatusEntrega.ENTREGUE);
            case FALHAR -> mudarStatus(entrega, StatusEntrega.FALHA);
            case PEDIR_AGUARDANDO -> mudarStatus(entrega, StatusEntrega.AGUARDANDO_DESPACHO);
            case PEDIR_DESPACHADA -> mudarStatus(entrega, StatusEntrega.DESPACHADA);
            case PEDIR_EM_ROTA -> mudarStatus(entrega, StatusEntrega.EM_ROTA);
            case PEDIR_CANCELADA -> mudarStatus(entrega, StatusEntrega.CANCELADA);
        };
        boolean doOperador = List.of(Operacao.DESPACHAR, Operacao.DEVOLVER, Operacao.CANCELAR).contains(operacao);
        requisicao.header(HttpHeaders.AUTHORIZATION, doOperador ? bearerOperador : bearerEntregador);
        return mockMvc.perform(requisicao).andReturn().getResponse();
    }

    private MockHttpServletRequestBuilder mudarStatus(String entrega, StatusEntrega destino) {
        return corpo(post("/api/entregador/entregas/" + entrega + "/status"),
                "{\"status\":\"%s\",\"motivoFalha\":\"x\",\"ocorridoEm\":\"%s\"}"
                        .formatted(destino, HorarioLocal.agora().withNano(0)));
    }

    private static MockHttpServletRequestBuilder corpo(MockHttpServletRequestBuilder requisicao, String corpo) {
        return requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private LoteEntrega lote(StatusLote status) {
        LoteEntrega lote = new LoteEntrega();
        lote.setEstabelecimento(loja);
        lote.setMotoboy(motoboy);
        lote.setUsuarioDespachante(operador);
        lote.setStatus(status);
        lote.setQtdEntregas((short) 1);
        return loteRepository.save(lote);
    }
}
