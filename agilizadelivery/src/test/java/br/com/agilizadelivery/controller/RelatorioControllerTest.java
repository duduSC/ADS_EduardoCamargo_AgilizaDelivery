package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;
import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.repository.ReciboRepository;
import br.com.agilizadelivery.repository.UsuarioRepository;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;
import br.com.agilizadelivery.service.CanalAcesso;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Relatorio simples do dia (D013, item 5; T015, item 2).
 *
 * As entregas consideradas sao as finalizadas no dia, no horario de Sao
 * Paulo, e todos os totais saem do servidor. Valor indefinido (null) nao
 * entra como zero.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RelatorioControllerTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 20);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private EntregaRepository entregaRepository;
    @Autowired
    private LoteEntregaRepository loteRepository;
    @Autowired
    private ReciboRepository reciboRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ServicoJwt servicoJwt;

    private Estabelecimento loja;
    private Usuario operador;
    private Caixa caixa;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        caixa = cenario.caixa(loja, DIA, StatusCaixa.ABERTO);
    }

    @Nested
    @DisplayName("Totais do dia")
    class Totais {

        @Test
        @DisplayName("Dia sem entregas: zeros, lista vazia e a data pedida")
        void diaVazio() throws Exception {
            relatorio(DIA, operador)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", is("2026-11-20")))
                    .andExpect(jsonPath("$.totalEntregas", is(0)))
                    .andExpect(jsonPath("$.entregues", is(0)))
                    .andExpect(jsonPath("$.falhas", is(0)))
                    .andExpect(jsonPath("$.canceladas", is(0)))
                    .andExpect(jsonPath("$.entregadoresQueTrabalharam", is(0)))
                    .andExpect(jsonPath("$.valorTotalRepasse").value(0))
                    .andExpect(jsonPath("$.valorPago").value(0))
                    .andExpect(jsonPath("$.valorAPagar").value(0))
                    .andExpect(jsonPath("$.porEntregador", hasSize(0)));
        }

        @Test
        @DisplayName("Entregadores com e sem recibo pago, FALHA e CANCELADA")
        void cenarioCompleto() throws Exception {
            Motoboy ana = entregador("Ana Souza");
            Motoboy bruno = entregador("Bruno Lima");
            LoteEntrega loteAna = lote(ana);
            LoteEntrega loteBruno = lote(bruno);

            Recibo pago = recibo(ana, StatusRecibo.PAGO);
            Recibo gerado = recibo(ana, StatusRecibo.GERADO);
            finalizada(loteAna, StatusEntrega.ENTREGUE, "9.00", DIA.atTime(11, 0), pago);
            finalizada(loteAna, StatusEntrega.ENTREGUE, "12.00", DIA.atTime(12, 0), gerado);
            finalizada(loteAna, StatusEntrega.FALHA, "9.00", DIA.atTime(13, 0), null);

            // Cancelada durante o percurso: repasse integral (DVP 1.5.5, passo 5)
            finalizada(loteBruno, StatusEntrega.ENTREGUE, "15.00", DIA.atTime(14, 0), null);
            finalizada(loteBruno, StatusEntrega.CANCELADA, "9.00", DIA.atTime(15, 0), null);

            // Cancelada antes do despacho: sem entregador e com repasse zero
            finalizada(null, StatusEntrega.CANCELADA, "0", DIA.atTime(9, 0), null);

            relatorio(DIA, operador)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalEntregas", is(6)))
                    .andExpect(jsonPath("$.entregues", is(3)))
                    .andExpect(jsonPath("$.falhas", is(1)))
                    .andExpect(jsonPath("$.canceladas", is(2)))
                    .andExpect(jsonPath("$.entregadoresQueTrabalharam", is(2)))
                    .andExpect(jsonPath("$.valorTotalRepasse").value(54.00))
                    .andExpect(jsonPath("$.valorPago").value(9.00))
                    .andExpect(jsonPath("$.valorAPagar").value(45.00))
                    .andExpect(jsonPath("$.porEntregador", hasSize(2)))
                    .andExpect(jsonPath("$.porEntregador[0].motoboyId", is(ana.getId().toString())))
                    .andExpect(jsonPath("$.porEntregador[0].nome", is("Ana Souza")))
                    .andExpect(jsonPath("$.porEntregador[0].entregas", is(3)))
                    .andExpect(jsonPath("$.porEntregador[0].entregues", is(2)))
                    .andExpect(jsonPath("$.porEntregador[0].falhas", is(1)))
                    .andExpect(jsonPath("$.porEntregador[0].valorRepasse").value(30.00))
                    .andExpect(jsonPath("$.porEntregador[0].valorPago").value(9.00))
                    .andExpect(jsonPath("$.porEntregador[0].valorAPagar").value(21.00))
                    .andExpect(jsonPath("$.porEntregador[1].nome", is("Bruno Lima")))
                    .andExpect(jsonPath("$.porEntregador[1].entregas", is(2)))
                    .andExpect(jsonPath("$.porEntregador[1].entregues", is(1)))
                    .andExpect(jsonPath("$.porEntregador[1].falhas", is(0)))
                    .andExpect(jsonPath("$.porEntregador[1].valorRepasse").value(24.00))
                    .andExpect(jsonPath("$.porEntregador[1].valorPago").value(0))
                    .andExpect(jsonPath("$.porEntregador[1].valorAPagar").value(24.00));
        }

        @Test
        @DisplayName("Valor de repasse indefinido não vira zero: o total e o a pagar ficam nulos")
        void valorIndefinido() throws Exception {
            Motoboy ana = entregador("Ana Souza");
            Motoboy bruno = entregador("Bruno Lima");
            Recibo pago = recibo(ana, StatusRecibo.PAGO);
            finalizada(lote(ana), StatusEntrega.ENTREGUE, "9.00", DIA.atTime(11, 0), pago);
            // Fora de area em loja sem valor configurado (DVP 1.5.5, passo 3)
            finalizada(lote(ana), StatusEntrega.ENTREGUE, null, DIA.atTime(12, 0), null);
            finalizada(lote(bruno), StatusEntrega.ENTREGUE, "12.00", DIA.atTime(13, 0), null);

            relatorio(DIA, operador)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalEntregas", is(3)))
                    .andExpect(jsonPath("$.valorTotalRepasse", nullValue()))
                    .andExpect(jsonPath("$.valorPago").value(9.00))
                    .andExpect(jsonPath("$.valorAPagar", nullValue()))
                    .andExpect(jsonPath("$.porEntregador[0].nome", is("Ana Souza")))
                    .andExpect(jsonPath("$.porEntregador[0].valorRepasse", nullValue()))
                    .andExpect(jsonPath("$.porEntregador[0].valorPago").value(9.00))
                    .andExpect(jsonPath("$.porEntregador[0].valorAPagar", nullValue()))
                    .andExpect(jsonPath("$.porEntregador[1].valorRepasse").value(12.00))
                    .andExpect(jsonPath("$.porEntregador[1].valorAPagar").value(12.00));
        }

        @Test
        @DisplayName("Entregas em andamento ficam de fora")
        void semEntregaEmAndamento() throws Exception {
            Motoboy ana = entregador("Ana Souza");
            LoteEntrega lote = lote(ana);
            finalizada(lote, StatusEntrega.ENTREGUE, "9.00", DIA.atTime(11, 0), null);
            Entrega emRota = cenario.entrega(loja, StatusEntrega.EM_ROTA);
            emRota.setLote(lote);
            entregaRepository.save(emRota);
            cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);

            relatorio(DIA, operador)
                    .andExpect(jsonPath("$.totalEntregas", is(1)))
                    .andExpect(jsonPath("$.porEntregador[0].entregas", is(1)));
        }

        @Test
        @DisplayName("Recibo pago com entrega de outro dia não paga o que é de hoje nem fica negativo")
        void reciboComEntregaDeOutroDia() throws Exception {
            Motoboy ana = entregador("Ana Souza");
            Recibo pago = recibo(ana, StatusRecibo.PAGO);
            finalizada(lote(ana), StatusEntrega.ENTREGUE, "9.00", DIA.minusDays(1).atTime(23, 30), pago);
            finalizada(lote(ana), StatusEntrega.ENTREGUE, "12.00", DIA.atTime(10, 0), null);

            relatorio(DIA, operador)
                    .andExpect(jsonPath("$.totalEntregas", is(1)))
                    .andExpect(jsonPath("$.valorTotalRepasse").value(12.00))
                    .andExpect(jsonPath("$.valorPago").value(0))
                    .andExpect(jsonPath("$.valorAPagar").value(12.00));
        }

        @Test
        @DisplayName("Recibo cancelado não conta como pago")
        void reciboCancelado() throws Exception {
            Motoboy ana = entregador("Ana Souza");
            recibo(ana, StatusRecibo.CANCELADO);
            // O cancelamento desvincula as entregas (UC03 A2)
            finalizada(lote(ana), StatusEntrega.ENTREGUE, "9.00", DIA.atTime(10, 0), null);

            relatorio(DIA, operador)
                    .andExpect(jsonPath("$.valorPago").value(0))
                    .andExpect(jsonPath("$.valorAPagar").value(9.00));
        }
    }

    @Nested
    @DisplayName("Limites do dia")
    class LimitesDoDia {

        @Test
        @DisplayName("Vale de 00:00:00 até antes da meia-noite seguinte")
        void meiaNoite() throws Exception {
            LoteEntrega lote = lote(entregador("Ana Souza"));
            finalizada(lote, StatusEntrega.ENTREGUE, "1.00", DIA.minusDays(1).atTime(23, 59, 59, 999_000_000), null);
            finalizada(lote, StatusEntrega.ENTREGUE, "2.00", DIA.atStartOfDay(), null);
            finalizada(lote, StatusEntrega.ENTREGUE, "4.00", DIA.atTime(23, 59, 59, 999_000_000), null);
            finalizada(lote, StatusEntrega.ENTREGUE, "8.00", DIA.plusDays(1).atStartOfDay(), null);

            relatorio(DIA, operador)
                    .andExpect(jsonPath("$.totalEntregas", is(2)))
                    .andExpect(jsonPath("$.valorTotalRepasse").value(6.00));
        }

        @Test
        @DisplayName("Sem data: o dia de hoje em São Paulo")
        void semData() throws Exception {
            LocalDate hoje = HorarioLocal.hoje();
            finalizada(lote(entregador("Ana Souza")), StatusEntrega.ENTREGUE, "9.00", hoje.atTime(0, 0, 1), null);
            finalizada(lote(entregador("Bruno Lima")), StatusEntrega.ENTREGUE, "9.00", hoje.minusDays(1).atTime(12, 0),
                    null);

            mockMvc.perform(get("/api/relatorios/dia").header(HttpHeaders.AUTHORIZATION, cenario.bearer(operador)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", is(hoje.toString())))
                    .andExpect(jsonPath("$.totalEntregas", is(1)))
                    .andExpect(jsonPath("$.porEntregador[0].nome", is("Ana Souza")));
        }

        @Test
        @DisplayName("Data inválida: 400 PARAMETRO_INVALIDO")
        void dataInvalida() throws Exception {
            mockMvc.perform(get("/api/relatorios/dia").param("data", "20/11/2026")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(operador)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("PARAMETRO_INVALIDO")));
        }
    }

    @Nested
    @DisplayName("Datas extremas")
    class DatasExtremas {

        @ParameterizedTest
        @ValueSource(strings = { "+999999999-12-31", "-999999999-01-01",
                "+10000-01-01", "0000-12-31" })
        @DisplayName("Ano fora de 1 a 9999: 400 PARAMETRO_INVALIDO")
        void foraDoIntervalo(String data) throws Exception {
            mockMvc.perform(get("/api/relatorios/dia").param("data", data)
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(operador)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("PARAMETRO_INVALIDO")));
        }

        @ParameterizedTest
        @ValueSource(strings = { "9999-12-31", "0001-01-01" })
        @DisplayName("Primeiro e último dia aceitos: 200 vazio")
        void nosLimites(String data) throws Exception {
            mockMvc.perform(get("/api/relatorios/dia").param("data", data)
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(operador)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", is(data)))
                    .andExpect(jsonPath("$.totalEntregas", is(0)));
        }
    }

    @Nested
    @DisplayName("Isolamento e acesso")
    class Isolamento {

        @Test
        @DisplayName("Cada loja vê só as próprias entregas e entregadores")
        void duasLojas() throws Exception {
            Estabelecimento outra = cenario.estabelecimento();
            Usuario operadorOutra = cenario.usuario(outra, PerfilUsuario.OPERADOR);
            Motoboy daOutra = entregadorDe(outra, "Carlos Dias");
            LoteEntrega loteOutra = loteDe(outra, daOutra, operadorOutra);
            Entrega entregaOutra = cenario.entrega(outra, StatusEntrega.ENTREGUE);
            entregaOutra.setLote(loteOutra);
            entregaOutra.setValorRepasse(new BigDecimal("50.00"));
            entregaOutra.setFinalizadaEm(DIA.atTime(10, 0));
            entregaRepository.save(entregaOutra);

            finalizada(lote(entregador("Ana Souza")), StatusEntrega.ENTREGUE, "9.00", DIA.atTime(11, 0), null);

            relatorio(DIA, operador)
                    .andExpect(jsonPath("$.totalEntregas", is(1)))
                    .andExpect(jsonPath("$.valorTotalRepasse").value(9.00))
                    .andExpect(jsonPath("$.porEntregador", hasSize(1)))
                    .andExpect(jsonPath("$.porEntregador[0].nome", is("Ana Souza")));

            relatorio(DIA, operadorOutra)
                    .andExpect(jsonPath("$.totalEntregas", is(1)))
                    .andExpect(jsonPath("$.valorTotalRepasse").value(50.00))
                    .andExpect(jsonPath("$.porEntregador[0].nome", is("Carlos Dias")));
        }

        @Test
        @DisplayName("ADMIN_RESTAURANTE também acessa")
        void adminDaLoja() throws Exception {
            relatorio(DIA, cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Entregador: 403")
        void perfilEntregador() throws Exception {
            relatorio(DIA, entregador("Ana Souza").getUsuario())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
        }

        @Test
        @DisplayName("Operador com token do canal APP: 403 (UC07 E4)")
        void canalApp() throws Exception {
            String token = servicoJwt.gerarTokenAcesso(
                    new UsuarioAutenticadoResponse(operador.getId(), loja.getId(), null, operador.getNome(),
                            PerfilUsuario.OPERADOR),
                    CanalAcesso.APP);
            mockMvc.perform(get("/api/relatorios/dia").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN_GERAL: 403")
        void adminGeral() throws Exception {
            relatorio(DIA, cenario.usuario(null, PerfilUsuario.ADMIN_GERAL)).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sem token: 401")
        void semToken() throws Exception {
            mockMvc.perform(get("/api/relatorios/dia")).andExpect(status().isUnauthorized());
        }
    }

    private ResultActions relatorio(LocalDate data, Usuario quem) throws Exception {
        return mockMvc.perform(get("/api/relatorios/dia")
                .param("data", data.toString())
                .header(HttpHeaders.AUTHORIZATION, cenario.bearer(quem)));
    }

    private Motoboy entregador(String nome) {
        return entregadorDe(loja, nome);
    }

    private Motoboy entregadorDe(Estabelecimento estabelecimento, String nome) {
        Motoboy motoboy = cenario.motoboy(estabelecimento);
        motoboy.getUsuario().setNome(nome);
        usuarioRepository.save(motoboy.getUsuario());
        return motoboy;
    }

    private LoteEntrega lote(Motoboy motoboy) {
        return loteDe(loja, motoboy, operador);
    }

    private LoteEntrega loteDe(Estabelecimento estabelecimento, Motoboy motoboy, Usuario despachante) {
        LoteEntrega lote = new LoteEntrega();
        lote.setEstabelecimento(estabelecimento);
        lote.setMotoboy(motoboy);
        lote.setUsuarioDespachante(despachante);
        lote.setStatus(StatusLote.CONCLUIDO);
        lote.setQtdEntregas((short) 1);
        return loteRepository.save(lote);
    }

    private Recibo recibo(Motoboy motoboy, StatusRecibo status) {
        Recibo recibo = cenario.recibo(loja, motoboy, caixa, operador);
        recibo.setStatus(status);
        if (status == StatusRecibo.PAGO) {
            recibo.setPagoEm(DIA.atTime(18, 0));
        }
        return reciboRepository.save(recibo);
    }

    private Entrega finalizada(LoteEntrega lote, StatusEntrega status, String repasse, LocalDateTime em,
            Recibo recibo) {
        Entrega entrega = cenario.entrega(loja, status);
        entrega.setLote(lote);
        entrega.setValorRepasse(repasse == null ? null : new BigDecimal(repasse));
        entrega.setFinalizadaEm(em);
        entrega.setRecibo(recibo);
        entrega.setNomeCliente("Cliente " + UUID.randomUUID().toString().substring(0, 8));
        return entregaRepository.save(entrega);
    }
}
