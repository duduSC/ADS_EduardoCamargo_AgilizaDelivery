package br.com.agilizadelivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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

import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.MotivoRevisao;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
import br.com.agilizadelivery.repository.EntregaAjusteValorRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.repository.ReciboRepository;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Constituicao 5.1, 5.2 e 5.4 ponta a ponta: cada escrita que afeta um
 * periodo fechado, e quais entregas um recibo leva.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegrasFinanceirasTest {

    private enum Escrita {
        GERAR_RECIBO("CAIXA_FECHADO"),
        PAGAR_RECIBO("CAIXA_FECHADO"),
        CANCELAR_RECIBO_GERADO("RECIBO_INALTERAVEL"),
        CANCELAR_RECIBO_PAGO("RECIBO_INALTERAVEL"),
        AJUSTAR("CAIXA_FECHADO"),
        CONTESTAR("CAIXA_FECHADO"),
        CONTESTAR_EM_RECIBO("CAIXA_FECHADO"),
        RESOLVER_COM_AJUSTE("CAIXA_FECHADO"),
        RESOLVER_SEM_AJUSTE("CAIXA_FECHADO"),
        FECHAR_DE_NOVO("CAIXA_FECHADO");

        private final String codigo;

        Escrita(String codigo) {
            this.codigo = codigo;
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private EntregaRepository entregaRepository;
    @Autowired
    private EntregaAjusteValorRepository ajusteRepository;
    @Autowired
    private LoteEntregaRepository loteRepository;
    @Autowired
    private ReciboRepository reciboRepository;

    private Estabelecimento loja;
    private Usuario operador;
    private Motoboy motoboy;
    private String bearer;
    private LocalDate hoje;
    private LocalDate ontem;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        motoboy = cenario.motoboy(loja);
        bearer = cenario.bearer(operador);
        hoje = HorarioLocal.hoje();
        ontem = hoje.minusDays(1);
    }

    @ParameterizedTest
    @EnumSource(Escrita.class)
    @DisplayName("Caixa fechado bloqueia toda escrita do período, sem alterar nada")
    void caixaFechadoBloqueia(Escrita escrita) throws Exception {
        Caixa caixa = cenario.caixa(loja, hoje, StatusCaixa.FECHADO);
        Entrega entrega = finalizada(motoboy, StatusEntrega.ENTREGUE, hoje.atTime(12, 0));
        Recibo recibo = null;
        switch (escrita) {
            case PAGAR_RECIBO, CANCELAR_RECIBO_GERADO, CANCELAR_RECIBO_PAGO, CONTESTAR_EM_RECIBO -> {
                recibo = cenario.recibo(loja, motoboy, caixa, operador);
                if (escrita == Escrita.CANCELAR_RECIBO_PAGO) {
                    recibo.setStatus(StatusRecibo.PAGO);
                }
                entrega.setRecibo(recibo);
            }
            case RESOLVER_COM_AJUSTE, RESOLVER_SEM_AJUSTE -> entrega.contestar();
            default -> {
            }
        }
        boolean pendenteAntes = entrega.getPendenteRevisao();
        UUID reciboAntes = recibo == null ? null : recibo.getId();
        StatusRecibo statusAntes = recibo == null ? null : recibo.getStatus();

        ResultActions resposta = switch (escrita) {
            case GERAR_RECIBO -> gerar(hoje, hoje);
            case PAGAR_RECIBO -> enviar(post("/api/recibos/" + reciboAntes + "/pagar"), bearer, null);
            case CANCELAR_RECIBO_GERADO, CANCELAR_RECIBO_PAGO -> enviar(
                    post("/api/recibos/" + reciboAntes + "/cancelar"), bearer, "{\"motivo\":\"x\"}");
            case AJUSTAR -> enviar(post("/api/entregas/" + entrega.getId() + "/ajustes"), bearer,
                    "{\"valorNovo\":12.00,\"motivo\":\"x\"}");
            case CONTESTAR, CONTESTAR_EM_RECIBO -> enviar(
                    post("/api/entregador/entregas/" + entrega.getId() + "/contestar"),
                    cenario.bearer(motoboy.getUsuario()), null);
            case RESOLVER_COM_AJUSTE -> enviar(post("/api/entregas/" + entrega.getId() + "/revisao/resolver"), bearer,
                    "{\"valorRepasse\":12.00,\"motivo\":\"x\"}");
            case RESOLVER_SEM_AJUSTE -> enviar(post("/api/entregas/" + entrega.getId() + "/revisao/resolver"), bearer,
                    "{\"motivo\":\"x\"}");
            case FECHAR_DE_NOVO -> enviar(post("/api/caixas/" + caixa.getId() + "/fechar"), bearer, null);
        };

        resposta.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo", is(escrita.codigo)));
        assertThat(entrega.getValorRepasse()).isEqualByComparingTo("9.00");
        assertThat(entrega.getPendenteRevisao()).isEqualTo(pendenteAntes);
        assertThat(entrega.getRecibo() == null ? null : entrega.getRecibo().getId()).isEqualTo(reciboAntes);
        if (recibo != null) {
            assertThat(recibo.getStatus()).isEqualTo(statusAntes);
        }
        assertThat(ajusteRepository.findByEntregaEstabelecimentoIdAndEntregaIdOrderByAjustadoEmAsc(
                loja.getId(), entrega.getId())).isEmpty();
    }

    @Test
    @DisplayName("Recibo de vários dias não leva entrega de dia com caixa fechado")
    void reciboNaoInvadeDiaFechado() throws Exception {
        cenario.caixa(loja, ontem, StatusCaixa.ABERTO);
        cenario.caixa(loja, hoje, StatusCaixa.FECHADO);
        Entrega deOntem = finalizada(motoboy, StatusEntrega.ENTREGUE, ontem.atTime(12, 0));
        Entrega doDiaFechado = finalizada(motoboy, StatusEntrega.ENTREGUE, hoje.atTime(12, 0));

        gerar(ontem, hoje)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("CAIXA_FECHADO")));

        assertThat(deOntem.getRecibo()).isNull();
        assertThat(doDiaFechado.getRecibo()).isNull();
    }

    @Test
    @DisplayName("Recibo de vários dias continua valendo com os dias abertos, reabertos ou sem caixa")
    void reciboDeVariosDiasAbertos() throws Exception {
        cenario.caixa(loja, ontem.minusDays(1), StatusCaixa.ABERTO);
        cenario.caixa(loja, hoje, StatusCaixa.REABERTO);
        finalizada(motoboy, StatusEntrega.ENTREGUE, ontem.minusDays(1).atTime(12, 0));
        finalizada(motoboy, StatusEntrega.ENTREGUE, ontem.atTime(12, 0));
        finalizada(motoboy, StatusEntrega.ENTREGUE, hoje.atTime(12, 0));

        gerar(ontem.minusDays(1), hoje)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.qtdEntregas", is(3)));
    }

    @Test
    @DisplayName("Cancelar recibo não solta entrega de dia com caixa fechado")
    void cancelamentoNaoInvadeDiaFechado() throws Exception {
        Recibo recibo = cenario.recibo(loja, motoboy, cenario.caixa(loja, ontem, StatusCaixa.ABERTO), operador);
        cenario.caixa(loja, hoje, StatusCaixa.FECHADO);
        Entrega entrega = finalizada(motoboy, StatusEntrega.ENTREGUE, hoje.atTime(12, 0));
        entrega.setRecibo(recibo);

        enviar(post("/api/recibos/" + recibo.getId() + "/cancelar"), bearer, "{\"motivo\":\"x\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("RECIBO_INALTERAVEL")));

        assertThat(entrega.getRecibo()).isNotNull();
        assertThat(recibo.getStatus()).isEqualTo(StatusRecibo.GERADO);
    }

    @Test
    @DisplayName("Recibo leva exatamente: estado final, sem revisão, sem recibo, do entregador e do período")
    void reciboLevaExatamenteAsCertas() throws Exception {
        Caixa caixa = cenario.caixa(loja);
        LocalDateTime meioDia = hoje.atTime(12, 0);
        Entrega entregue = finalizada(motoboy, StatusEntrega.ENTREGUE, meioDia);
        Entrega falha = finalizada(motoboy, StatusEntrega.FALHA, meioDia);
        Entrega cancelada = finalizada(motoboy, StatusEntrega.CANCELADA, meioDia);

        for (MotivoRevisao motivo : MotivoRevisao.values()) {
            Entrega emRevisao = finalizada(motoboy, StatusEntrega.ENTREGUE, meioDia);
            emRevisao.setPendenteRevisao(true);
            emRevisao.setMotivoRevisao(motivo);
        }
        finalizada(motoboy, StatusEntrega.ENTREGUE, meioDia)
                .setRecibo(cenario.recibo(loja, motoboy, caixa, operador));
        finalizada(motoboy, StatusEntrega.EM_ROTA, null);
        finalizada(cenario.motoboy(loja), StatusEntrega.ENTREGUE, meioDia);
        finalizada(motoboy, StatusEntrega.ENTREGUE, ontem.atTime(23, 59, 59));
        entregaRepository.flush();

        String id = JsonPath.read(gerar(hoje, hoje)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.qtdEntregas", is(3)))
                .andExpect(jsonPath("$.valorTotal", is(27.0)))
                .andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(entregaRepository.findByReciboId(UUID.fromString(id)))
                .extracting(Entrega::getId)
                .containsExactlyInAnyOrderElementsOf(
                        List.of(entregue.getId(), falha.getId(), cancelada.getId()));
        assertThat(reciboRepository.findByEstabelecimentoIdAndMotoboyId(loja.getId(), motoboy.getId())).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(value = StatusEntrega.class, names = { "AGUARDANDO_DESPACHO", "DESPACHADA", "EM_ROTA" })
    @DisplayName("Ajuste em entrega não finalizada é recusado: o congelamento o descartaria")
    void ajusteSoDepoisDeFinalizada(StatusEntrega status) throws Exception {
        cenario.caixa(loja);
        Entrega entrega = cenario.entrega(loja, status);

        enviar(post("/api/entregas/" + entrega.getId() + "/ajustes"), bearer, "{\"valorNovo\":20.00,\"motivo\":\"x\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("AJUSTE_VALOR_NAO_PERMITIDO")));

        assertThat(entrega.getValorRepasse()).isEqualByComparingTo("9.00");
    }

    @ParameterizedTest
    @ValueSource(strings = { "00:00:00", "23:59:59" })
    @DisplayName("O dia do acerto vai de 00:00:00 a 23:59:59 e não pega a meia-noite seguinte")
    void limitesDoDiaDoAcerto(String hora) throws Exception {
        finalizada(motoboy, StatusEntrega.ENTREGUE, LocalDateTime.parse(hoje + "T" + hora));
        finalizada(motoboy, StatusEntrega.ENTREGUE, hoje.plusDays(1).atStartOfDay());
        entregaRepository.flush();

        enviar(get("/api/entregador/acerto"), cenario.bearer(motoboy.getUsuario()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extrato.linhas", hasSize(1)));
    }

    private ResultActions gerar(LocalDate de, LocalDate ate) throws Exception {
        return enviar(post("/api/recibos"), bearer, """
                {"motoboyId":"%s","periodoInicio":"%sT00:00:00","periodoFim":"%sT23:59:59"}
                """.formatted(motoboy.getId(), de, ate));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder requisicao, String autorizacao, String corpo)
            throws Exception {
        requisicao.header(HttpHeaders.AUTHORIZATION, autorizacao);
        if (corpo != null) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mockMvc.perform(requisicao);
    }

    private Entrega finalizada(Motoboy dono, StatusEntrega status, LocalDateTime finalizadaEm) {
        LoteEntrega lote = new LoteEntrega();
        lote.setEstabelecimento(loja);
        lote.setMotoboy(dono);
        lote.setUsuarioDespachante(operador);
        lote.setStatus(status.finalizado() ? StatusLote.CONCLUIDO : StatusLote.EM_ROTA);
        lote.setQtdEntregas((short) 1);
        loteRepository.save(lote);
        Entrega entrega = cenario.entrega(loja, status);
        entrega.setValorRepasse(new BigDecimal("9.00"));
        entrega.setLote(lote);
        entrega.setFinalizadaEm(finalizadaEm);
        return entregaRepository.save(entrega);
    }
}
