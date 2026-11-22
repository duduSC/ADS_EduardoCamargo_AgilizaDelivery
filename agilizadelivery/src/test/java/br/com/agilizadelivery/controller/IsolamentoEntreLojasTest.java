package br.com.agilizadelivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.DistanciaPreco;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.FalhaIntegracao;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.EtapaFalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusFalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import br.com.agilizadelivery.repository.DistanciaPrecoRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.FalhaIntegracaoRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * IDOR com dois estabelecimentos para cada recurso (Constituicao 5.6): a
 * loja A usa ids validos da loja B. A resposta e a de id inexistente, e
 * nada muda em B.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IsolamentoEntreLojasTest {

    private enum Quem {
        OPERADOR, ADMIN, ENTREGADOR, ENTREGADOR_COLEGA
    }

    private record Ataque(Quem quem, String metodo, String caminho, String corpo, int esperado) {

        @Override
        public String toString() {
            return quem + " " + metodo + " " + caminho;
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private EntregaRepository entregaRepository;
    @Autowired
    private LoteEntregaRepository loteRepository;
    @Autowired
    private DistanciaPrecoRepository faixaRepository;
    @Autowired
    private ConfiguracaoIntegracaoRepository configuracaoRepository;
    @Autowired
    private FalhaIntegracaoRepository falhaRepository;

    private Estabelecimento lojaB;
    private Motoboy motoboyA;
    private Motoboy motoboyB;
    private Entrega entregaA;
    private Entrega pendenteB;
    private Entrega emRotaB;
    private Entrega finalizadaB;
    private Entrega contestadaB;
    private LoteEntrega loteB;
    private LoteEntrega loteEmRotaB;
    private Recibo reciboB;
    private Caixa caixaB;
    private Caixa caixaFechadoB;
    private FalhaIntegracao falhaB;

    private String operadorA;
    private String adminA;
    private String entregadorA;
    private String colegaB;
    private String operadorB;
    private String adminB;

    @BeforeEach
    void setUp() {
        Estabelecimento lojaA = cenario.estabelecimento();
        operadorA = cenario.bearer(cenario.usuario(lojaA, PerfilUsuario.OPERADOR));
        adminA = cenario.bearer(cenario.usuario(lojaA, PerfilUsuario.ADMIN_RESTAURANTE));
        motoboyA = cenario.motoboy(lojaA);
        entregadorA = cenario.bearer(motoboyA.getUsuario());
        entregaA = cenario.entrega(lojaA, StatusEntrega.AGUARDANDO_DESPACHO);
        cenario.caixa(lojaA);

        lojaB = cenario.estabelecimento();
        Usuario operador = cenario.usuario(lojaB, PerfilUsuario.OPERADOR);
        operadorB = cenario.bearer(operador);
        adminB = cenario.bearer(cenario.usuario(lojaB, PerfilUsuario.ADMIN_RESTAURANTE));
        motoboyB = cenario.motoboy(lojaB);
        colegaB = cenario.bearer(cenario.motoboy(lojaB).getUsuario());

        loteB = lote(operador, StatusLote.DESPACHADO);
        noLote(cenario.entrega(lojaB, StatusEntrega.DESPACHADA), loteB);
        loteEmRotaB = lote(operador, StatusLote.EM_ROTA);
        emRotaB = noLote(cenario.entrega(lojaB, StatusEntrega.EM_ROTA), loteEmRotaB);
        LoteEntrega concluido = lote(operador, StatusLote.CONCLUIDO);
        finalizadaB = noLote(cenario.entrega(lojaB, StatusEntrega.ENTREGUE), concluido);
        finalizadaB.setFinalizadaEm(HorarioLocal.hoje().atTime(12, 0));
        contestadaB = noLote(cenario.entrega(lojaB, StatusEntrega.ENTREGUE), concluido);
        contestadaB.setFinalizadaEm(HorarioLocal.hoje().atTime(12, 5));
        contestadaB.contestar();
        pendenteB = cenario.entrega(lojaB, StatusEntrega.AGUARDANDO_DESPACHO);

        caixaB = cenario.caixa(lojaB);
        caixaFechadoB = cenario.caixa(lojaB, HorarioLocal.hoje().minusDays(1), StatusCaixa.FECHADO);
        reciboB = cenario.recibo(lojaB, motoboyB, caixaB, operador);

        DistanciaPreco faixa = new DistanciaPreco();
        faixa.setEstabelecimento(lojaB);
        faixa.setDeKm(BigDecimal.ZERO);
        faixa.setAteKm(new BigDecimal("4.00"));
        faixa.setValorPago(new BigDecimal("7.77"));
        faixa.setVigenteDesde(HorarioLocal.hoje());
        faixa.setAtivo(true);
        faixaRepository.save(faixa);

        ConfiguracaoIntegracao configuracao = new ConfiguracaoIntegracao();
        configuracao.setEstabelecimento(lojaB);
        configuracao.setProvedor("SIMULADOR");
        configuracao.setMerchantId("merchant-" + lojaB.getId());
        configuracao.setAtivo(true);
        configuracaoRepository.save(configuracao);

        falhaB = new FalhaIntegracao();
        falhaB.setConfiguracaoIntegracao(configuracao);
        falhaB.setEtapa(EtapaFalhaIntegracao.CONSULTA);
        falhaB.setMensagemErro("falha");
        falhaB.setTentativas((short) 5);
        falhaB.setStatus(StatusFalhaIntegracao.PENDENTE);
        falhaB.setOcorridoEm(HorarioLocal.agora());
        falhaRepository.save(falhaB);
        entregaRepository.flush();
    }

    static Stream<Ataque> ataques() {
        String status = "{\"status\":\"ENTREGUE\",\"ocorridoEm\":\"{hoje}T12:30:00\"}";
        String falhou = "{\"status\":\"FALHA\",\"motivoFalha\":\"x\",\"ocorridoEm\":\"{hoje}T12:30:00\"}";
        String periodo = "\"periodoInicio\":\"{hoje}T00:00:00\",\"periodoFim\":\"{hoje}T23:59:59\"";
        return Stream.of(
                // entrega
                new Ataque(Quem.OPERADOR, "GET", "/api/entregas/{pendente}", null, 404),
                new Ataque(Quem.OPERADOR, "POST", "/api/entregas/{pendente}/pronto", null, 404),
                new Ataque(Quem.OPERADOR, "PATCH", "/api/entregas/{pendente}/coordenada",
                        "{\"latitude\":-23.5,\"longitude\":-46.6}", 404),
                new Ataque(Quem.OPERADOR, "POST", "/api/entregas/{pendente}/cancelar", "{\"motivo\":\"x\"}", 404),
                new Ataque(Quem.ADMIN, "POST", "/api/entregas/{emRota}/cancelar", "{\"motivo\":\"x\"}", 404),
                new Ataque(Quem.OPERADOR, "POST", "/api/entregas/{contestada}/revisao/resolver",
                        "{\"motivo\":\"x\"}", 404),
                new Ataque(Quem.OPERADOR, "POST", "/api/entregas/{finalizada}/ajustes",
                        "{\"valorNovo\":99.00,\"motivo\":\"x\"}", 404),
                // lote
                new Ataque(Quem.OPERADOR, "POST", "/api/lotes",
                        "{\"motoboyId\":\"{motoboyA}\",\"entregaIds\":[\"{pendente}\"]}", 404),
                new Ataque(Quem.OPERADOR, "POST", "/api/lotes",
                        "{\"motoboyId\":\"{motoboyA}\",\"entregaIds\":[\"{entregaA}\",\"{pendente}\"]}", 404),
                new Ataque(Quem.OPERADOR, "POST", "/api/lotes",
                        "{\"motoboyId\":\"{motoboy}\",\"entregaIds\":[\"{entregaA}\"]}", 404),
                new Ataque(Quem.OPERADOR, "GET", "/api/lotes/{lote}", null, 404),
                new Ataque(Quem.OPERADOR, "GET", "/api/lotes/{lote}/rota", null, 404),
                new Ataque(Quem.OPERADOR, "POST", "/api/lotes/{lote}/devolver", null, 404),
                // entregador visto pelo backoffice
                new Ataque(Quem.OPERADOR, "GET", "/api/motoboys/{motoboy}", null, 404),
                new Ataque(Quem.ADMIN, "PUT", "/api/motoboys/{motoboy}", "{\"telefone\":\"54999990000\"}", 404),
                new Ataque(Quem.ADMIN, "POST", "/api/motoboys/{motoboy}/bloquear", null, 404),
                new Ataque(Quem.ADMIN, "POST", "/api/motoboys/{motoboy}/desbloquear", null, 404),
                // acerto, recibo e caixa
                new Ataque(Quem.OPERADOR, "GET",
                        "/api/acertos?motoboyId={motoboy}&inicio={hoje}T00:00:00&fim={hoje}T23:59:59", null, 404),
                new Ataque(Quem.OPERADOR, "POST", "/api/recibos", "{\"motoboyId\":\"{motoboy}\"," + periodo + "}", 404),
                new Ataque(Quem.OPERADOR, "GET", "/api/recibos/{recibo}", null, 404),
                new Ataque(Quem.OPERADOR, "POST", "/api/recibos/{recibo}/pagar", null, 404),
                new Ataque(Quem.OPERADOR, "POST", "/api/recibos/{recibo}/cancelar", "{\"motivo\":\"x\"}", 404),
                new Ataque(Quem.OPERADOR, "POST", "/api/caixas/{caixa}/fechar", null, 404),
                new Ataque(Quem.ADMIN, "POST", "/api/caixas/{caixaFechado}/reabrir", "{\"motivo\":\"x\"}", 404),
                // integracao
                new Ataque(Quem.ADMIN, "POST", "/api/falhas-integracao/{falha}/reprocessar", null, 404),
                new Ataque(Quem.ADMIN, "POST", "/api/falhas-integracao/{falha}/descartar", null, 404),
                new Ataque(Quem.ADMIN, "PUT", "/api/integracoes",
                        "{\"provedor\":\"SIMULADOR\",\"merchantId\":\"merchant-{loja}\"}", 409),
                // plataforma
                new Ataque(Quem.ADMIN, "GET", "/api/estabelecimentos/{loja}", null, 403),
                new Ataque(Quem.ADMIN, "PATCH", "/api/estabelecimentos/{loja}/status",
                        "{\"status\":\"INATIVO\"}", 403),
                // app: outra loja e colega da mesma loja
                new Ataque(Quem.ENTREGADOR, "POST", "/api/entregador/lotes/{lote}/iniciar", null, 404),
                new Ataque(Quem.ENTREGADOR, "POST", "/api/entregador/entregas/{emRota}/status", status, 404),
                new Ataque(Quem.ENTREGADOR, "POST", "/api/entregador/entregas/{emRota}/status", falhou, 404),
                new Ataque(Quem.ENTREGADOR, "POST", "/api/entregador/entregas/{finalizada}/contestar", null, 404),
                new Ataque(Quem.ENTREGADOR, "PATCH", "/api/motoboys/{motoboy}/posicao",
                        "{\"latitude\":-23.5,\"longitude\":-46.6}", 404),
                new Ataque(Quem.ENTREGADOR_COLEGA, "POST", "/api/entregador/lotes/{lote}/iniciar", null, 404),
                new Ataque(Quem.ENTREGADOR_COLEGA, "POST", "/api/entregador/entregas/{emRota}/status", status, 404),
                new Ataque(Quem.ENTREGADOR_COLEGA, "POST", "/api/entregador/entregas/{finalizada}/contestar",
                        null, 404),
                new Ataque(Quem.ENTREGADOR_COLEGA, "PATCH", "/api/motoboys/{motoboy}/posicao",
                        "{\"latitude\":-23.5,\"longitude\":-46.6}", 404));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ataques")
    @DisplayName("Id de outra loja responde como inexistente e não altera nada")
    void idDeOutraLoja(Ataque ataque) throws Exception {
        List<String> antes = retratoDaLojaB();

        MockHttpServletRequestBuilder requisicao = MockMvcRequestBuilders
                .request(HttpMethod.valueOf(ataque.metodo()), preencher(ataque.caminho()))
                .header(HttpHeaders.AUTHORIZATION, bearer(ataque.quem()));
        if (ataque.corpo() != null) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content(preencher(ataque.corpo()));
        }
        MvcResult resultado = mockMvc.perform(requisicao).andReturn();

        assertThat(resultado.getResponse().getStatus())
                .as(resultado.getResponse().getContentAsString())
                .isEqualTo(ataque.esperado());
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("nomeCliente", "telefone", lojaB.getRazaoSocial());
        assertThat(retratoDaLojaB()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Nenhuma listagem da loja A contém registro da loja B")
    void listagensFiltradas() throws Exception {
        List<String> caminhos = new ArrayList<>(List.of(
                "/api/estabelecimento", "/api/motoboys", "/api/motoboys?statusDisponibilidade=ONLINE",
                "/api/motoboys?statusCadastro=ATIVO", "/api/recibos", "/api/recibos?caixaId={caixa}",
                "/api/recibos?motoboyId={motoboy}", "/api/caixas", "/api/caixas/atual", "/api/faixas-preco",
                "/api/integracoes", "/api/falhas-integracao", "/api/usuarios", "/api/entregas",
                "/api/entregas?pendenteRevisao=true", "/api/entregas/{finalizada}/ajustes"));
        for (StatusEntrega status : StatusEntrega.values()) {
            caminhos.add("/api/entregas?status=" + status);
        }
        for (StatusLote status : StatusLote.values()) {
            caminhos.add("/api/lotes?status=" + status);
        }

        for (String caminho : caminhos) {
            String corpo = consultar(preencher(caminho), adminA);
            assertThat(corpo).as(caminho).doesNotContain(
                    lojaB.getId().toString(), pendenteB.getId().toString(), motoboyB.getId().toString(),
                    loteEmRotaB.getId().toString(), reciboB.getId().toString(), caixaB.getId().toString(), "7.77");
        }
    }

    private List<String> retratoDaLojaB() throws Exception {
        List<String> caminhos = new ArrayList<>(List.of(
                "/api/estabelecimento", "/api/motoboys", "/api/caixas", "/api/faixas-preco", "/api/integracoes",
                "/api/falhas-integracao", "/api/recibos?motoboyId=" + motoboyB.getId(),
                "/api/entregas/" + finalizadaB.getId() + "/ajustes", "/api/entregas?pendenteRevisao=true"));
        for (StatusEntrega status : StatusEntrega.values()) {
            caminhos.add("/api/entregas?status=" + status);
        }
        for (StatusLote status : StatusLote.values()) {
            caminhos.add("/api/lotes?status=" + status);
        }
        List<String> retrato = new ArrayList<>();
        for (String caminho : caminhos) {
            boolean soAdministrador = caminho.contains("faixas") || caminho.contains("integracoes");
            retrato.add(consultar(caminho, soAdministrador ? adminB : operadorB));
        }
        return retrato;
    }

    private String consultar(String caminho, String bearer) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(caminho).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String bearer(Quem quem) {
        return switch (quem) {
            case OPERADOR -> operadorA;
            case ADMIN -> adminA;
            case ENTREGADOR -> entregadorA;
            case ENTREGADOR_COLEGA -> colegaB;
        };
    }

    private String preencher(String modelo) {
        return modelo
                .replace("{pendente}", pendenteB.getId().toString())
                .replace("{emRota}", emRotaB.getId().toString())
                .replace("{finalizada}", finalizadaB.getId().toString())
                .replace("{contestada}", contestadaB.getId().toString())
                .replace("{lote}", loteB.getId().toString())
                .replace("{recibo}", reciboB.getId().toString())
                .replace("{caixa}", caixaB.getId().toString())
                .replace("{caixaFechado}", caixaFechadoB.getId().toString())
                .replace("{falha}", falhaB.getId().toString())
                .replace("{motoboy}", motoboyB.getId().toString())
                .replace("{motoboyA}", motoboyA.getId().toString())
                .replace("{entregaA}", entregaA.getId().toString())
                .replace("{loja}", lojaB.getId().toString())
                .replace("{hoje}", HorarioLocal.hoje().toString());
    }

    private LoteEntrega lote(Usuario operador, StatusLote status) {
        LoteEntrega lote = new LoteEntrega();
        lote.setEstabelecimento(lojaB);
        lote.setMotoboy(motoboyB);
        lote.setUsuarioDespachante(operador);
        lote.setStatus(status);
        lote.setQtdEntregas((short) 1);
        return loteRepository.save(lote);
    }

    private Entrega noLote(Entrega entrega, LoteEntrega lote) {
        entrega.setLote(lote);
        entrega.setOrdemRota((short) 1);
        return entregaRepository.save(entrega);
    }
}
