package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.EntregaStatusHistorico;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.ItemPedido;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.EntregaStatusHistoricoRepository;
import br.com.agilizadelivery.repository.PedidoExternoRepository;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;
import br.com.agilizadelivery.service.CanalAcesso;
import br.com.agilizadelivery.tempo.HorarioLocal;
import br.com.agilizadelivery.websocket.PublicadorTempoReal;

/**
 * Testes de integracao do EntregadorController (T009) e da correcao do
 * PATCH /api/motoboys/{id}/posicao.
 *
 * Os lotes sao despachados pela API do operador, como no uso real; o
 * publicador de tempo real e substituido por um mock para conferir os
 * eventos enviados ao painel (HU05).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EntregadorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioWeb cenario;

    @Autowired
    private EntregaRepository entregaRepository;

    @Autowired
    private PedidoExternoRepository pedidoExternoRepository;

    @Autowired
    private EntregaStatusHistoricoRepository historicoRepository;

    @Autowired
    private ServicoJwt servicoJwt;

    @MockitoBean
    private PublicadorTempoReal publicador;

    private Estabelecimento loja;
    private String bearerOperador;
    private Motoboy motoboy;
    private String bearerEntregador;
    private Motoboy outroMotoboy;
    private String bearerOutro;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        bearerOperador = cenario.bearer(cenario.usuario(loja, PerfilUsuario.OPERADOR));
        motoboy = cenario.motoboy(loja);
        bearerEntregador = cenario.bearer(motoboy.getUsuario());
        outroMotoboy = cenario.motoboy(loja);
        bearerOutro = cenario.bearer(outroMotoboy.getUsuario());
    }

    private UUID despachar(Motoboy destino, Entrega... entregas) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (Entrega entrega : entregas) {
            if (!ids.isEmpty()) {
                ids.append(',');
            }
            ids.append('"').append(entrega.getId()).append('"');
        }
        MvcResult resultado = mockMvc.perform(post("/api/lotes")
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motoboyId\":\"" + destino.getId() + "\",\"entregaIds\":[" + ids + "]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.id"));
    }

    private void iniciar(UUID loteId) throws Exception {
        mockMvc.perform(post("/api/entregador/lotes/" + loteId + "/iniciar")
                        .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                .andExpect(status().isOk());
    }

    private static String corpoStatus(String status, String motivo) {
        String motivoJson = motivo == null ? "null" : "\"" + motivo + "\"";
        return """
                {"status": "%s", "motivoFalha": %s,
                 "coordenada": {"latitude": -23.5614000, "longitude": -46.6559000},
                 "ocorridoEm": "%s"}
                """.formatted(status, motivoJson, HorarioLocal.agora().withNano(0));
    }

    /** Despacha, inicia e marca a entrega ENTREGUE pelo entregador principal. */
    private Entrega entregaConcluida() throws Exception {
        Entrega entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
        iniciar(despachar(motoboy, entrega));
        mockMvc.perform(post("/api/entregador/entregas/" + entrega.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoStatus("ENTREGUE", null)))
                .andExpect(status().isOk());
        return entrega;
    }

    @Nested
    @DisplayName("Autorizacao")
    class Autorizacao {

        @Test
        @DisplayName("GET /me devolve o proprio entregador")
        void deveDevolverProprio() throws Exception {
            mockMvc.perform(get("/api/entregador/me")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(motoboy.getId().toString())))
                    .andExpect(jsonPath("$.statusDisponibilidade", is("ONLINE")));
        }

        @Test
        @DisplayName("OPERADOR recebe 403 nos endpoints do entregador")
        void deveNegarOperador() throws Exception {
            mockMvc.perform(get("/api/entregador/me")
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/entregador/lote-ativo")
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/entregador/acerto")
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sem token: 401")
        void deveExigirToken() throws Exception {
            mockMvc.perform(get("/api/entregador/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ENTREGADOR com token do canal BACKOFFICE: 403 (UC07 E4)")
        void deveExigirCanalApp() throws Exception {
            Usuario usuario = motoboy.getUsuario();
            String token = servicoJwt.gerarTokenAcesso(
                    new UsuarioAutenticadoResponse(usuario.getId(), loja.getId(), null, usuario.getNome(),
                            PerfilUsuario.ENTREGADOR),
                    CanalAcesso.BACKOFFICE);

            mockMvc.perform(get("/api/entregador/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
        }
    }

    @Nested
    @DisplayName("PATCH /api/entregador/disponibilidade")
    class Disponibilidade {

        @Test
        @DisplayName("OFFLINE -> ONLINE: 200 e publica na frota")
        void deveFicarOnline() throws Exception {
            Motoboy offline = cenario.motoboy(loja, StatusCadastroMotoboy.ATIVO,
                    StatusDisponibilidadeMotoboy.OFFLINE);

            mockMvc.perform(patch("/api/entregador/disponibilidade")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(offline.getUsuario()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"ONLINE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusDisponibilidade", is("ONLINE")));

            verify(publicador).publicarFrota(eq(loja.getId()), eq("DISPONIBILIDADE_ALTERADA"), any());
        }

        @Test
        @DisplayName("ONLINE -> OFFLINE: 200")
        void deveFicarOffline() throws Exception {
            mockMvc.perform(patch("/api/entregador/disponibilidade")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"OFFLINE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusDisponibilidade", is("OFFLINE")));
        }

        @Test
        @DisplayName("Pedir EM_ROTA: 400 DADOS_INVALIDOS")
        void deveRecusarEmRota() throws Exception {
            mockMvc.perform(patch("/api/entregador/disponibilidade")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"EM_ROTA\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("DADOS_INVALIDOS")));
        }

        @Test
        @DisplayName("Ficar OFFLINE estando EM_ROTA: 422 TRANSICAO_ESTADO_INVALIDA")
        void deveRecusarOfflineEmRota() throws Exception {
            despachar(motoboy, cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO));

            mockMvc.perform(patch("/api/entregador/disponibilidade")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"OFFLINE\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("TRANSICAO_ESTADO_INVALIDA")));
        }
    }

    @Nested
    @DisplayName("Posicao")
    class Posicao {

        private static final String CORPO = "{\"latitude\": -23.5600000, \"longitude\": -46.6400000}";

        @Test
        @DisplayName("PATCH /api/entregador/posicao: 200 com o entregador do token")
        void deveAtualizarPosicao() throws Exception {
            mockMvc.perform(patch("/api/entregador/posicao")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(motoboy.getId().toString())))
                    .andExpect(jsonPath("$.ultimaPosicao.latitude").value(-23.56));
        }

        @Test
        @DisplayName("PATCH /api/entregador/posicao em OFFLINE: 422 (RNF07)")
        void deveRecusarPosicaoOffline() throws Exception {
            Motoboy offline = cenario.motoboy(loja, StatusCadastroMotoboy.ATIVO,
                    StatusDisponibilidadeMotoboy.OFFLINE);

            mockMvc.perform(patch("/api/entregador/posicao")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(offline.getUsuario()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("PATCH /api/motoboys/{id}/posicao com o proprio id: 200")
        void deveAceitarLegadoComProprioId() throws Exception {
            mockMvc.perform(patch("/api/motoboys/" + motoboy.getId() + "/posicao")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PATCH /api/motoboys/{id}/posicao com id de outro entregador: 404, sem alterar")
        void deveRecusarLegadoComIdAlheio() throws Exception {
            mockMvc.perform(patch("/api/motoboys/" + outroMotoboy.getId() + "/posicao")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo", is("RECURSO_NAO_ENCONTRADO")));

            assertEquals(null, outroMotoboy.getUltimaPosicao());
        }
    }

    @Nested
    @DisplayName("GET /api/entregador/lote-ativo e POST /lotes/{id}/iniciar")
    class Lote {

        @Test
        @DisplayName("Sem lote: 204")
        void deveResponder204SemLote() throws Exception {
            mockMvc.perform(get("/api/entregador/lote-ativo")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Lote despachado: entregas na ordem da rota, com itens do pedido")
        void deveDevolverLoteComItens() throws Exception {
            PedidoExterno pedido = new PedidoExterno();
            pedido.setEstabelecimento(loja);
            pedido.setProvedor("SIMULADOR");
            pedido.setIdExterno("ext-" + UUID.randomUUID());
            pedido.setNomeCliente("Cliente");
            pedido.setValorTotal(new BigDecimal("50.00"));
            pedido.setPagoOnline(false);
            pedido.setPayloadJson("{}");
            ItemPedido item = new ItemPedido();
            item.setDescricao("Pizza calabresa");
            item.setQuantidade((short) 2);
            item.setObservacao("sem cebola");
            pedido.adicionarItem(item);
            pedidoExternoRepository.save(pedido);

            Entrega segunda = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            segunda.setDistanciaKm(null);
            segunda.setValorRepasse(null);
            entregaRepository.save(segunda);
            Entrega primeira = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            primeira.setPedidoExterno(pedido);
            entregaRepository.save(primeira);
            UUID loteId = despachar(motoboy, primeira, segunda);

            mockMvc.perform(get("/api/entregador/lote-ativo")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(loteId.toString())))
                    .andExpect(jsonPath("$.status", is("DESPACHADO")))
                    .andExpect(jsonPath("$.nomeFantasiaEstabelecimento", is("Loja de teste")))
                    .andExpect(jsonPath("$.entregas", hasSize(2)))
                    .andExpect(jsonPath("$.entregas[0].distanciaKm").value(2.10))
                    .andExpect(jsonPath("$.entregas[0].valorRepasse").value(9.00))
                    .andExpect(jsonPath("$.entregas[1].distanciaKm", nullValue()))
                    .andExpect(jsonPath("$.entregas[1].valorRepasse", nullValue()))
                    .andExpect(jsonPath("$.entregas[0].faixaPrecoId").doesNotExist())
                    .andExpect(jsonPath("$.entregas[0].reciboId").doesNotExist())
                    .andExpect(jsonPath("$.entregas[0].id", is(primeira.getId().toString())))
                    .andExpect(jsonPath("$.entregas[0].ordemRota", is(1)))
                    .andExpect(jsonPath("$.entregas[0].itens", hasSize(1)))
                    .andExpect(jsonPath("$.entregas[0].itens[0].descricao", is("Pizza calabresa")))
                    .andExpect(jsonPath("$.entregas[0].itens[0].quantidade", is(2)))
                    .andExpect(jsonPath("$.entregas[0].itens[0].observacao", is("sem cebola")))
                    .andExpect(jsonPath("$.entregas[1].id", is(segunda.getId().toString())))
                    .andExpect(jsonPath("$.entregas[1].itens", hasSize(0)))
                    .andExpect(jsonPath("$.usuarioDespachanteId").doesNotExist());
        }

        @Test
        @DisplayName("Lote de outro entregador nao aparece")
        void naoDeveMostrarLoteAlheio() throws Exception {
            despachar(outroMotoboy, cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO));

            mockMvc.perform(get("/api/entregador/lote-ativo")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Iniciar o proprio lote: lote e entregas EM_ROTA")
        void deveIniciarLote() throws Exception {
            UUID loteId = despachar(motoboy, cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO));

            mockMvc.perform(post("/api/entregador/lotes/" + loteId + "/iniciar")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("EM_ROTA")))
                    .andExpect(jsonPath("$.entregas[0].status", is("EM_ROTA")));

            verify(publicador, atLeastOnce()).publicarEntrega(eq(loja.getId()), eq("STATUS_ALTERADO"), any());
        }

        @Test
        @DisplayName("Iniciar lote de outro entregador: 404, como id inexistente")
        void deveRecusarLoteAlheio() throws Exception {
            UUID loteAlheio = despachar(outroMotoboy, cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO));

            mockMvc.perform(post("/api/entregador/lotes/" + loteAlheio + "/iniciar")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo", is("RECURSO_NAO_ENCONTRADO")));
            mockMvc.perform(post("/api/entregador/lotes/" + UUID.randomUUID() + "/iniciar")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo", is("RECURSO_NAO_ENCONTRADO")));
        }

        @Test
        @DisplayName("Lote de outra loja: 404")
        void deveRecusarLoteDeOutraLoja() throws Exception {
            Estabelecimento lojaB = cenario.estabelecimento();
            Motoboy motoboyB = cenario.motoboy(lojaB);
            String operadorB = cenario.bearer(cenario.usuario(lojaB, PerfilUsuario.OPERADOR));
            Entrega entregaB = cenario.entrega(lojaB, StatusEntrega.AGUARDANDO_DESPACHO);
            MvcResult resultado = mockMvc.perform(post("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, operadorB)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"motoboyId\":\"" + motoboyB.getId() + "\",\"entregaIds\":[\""
                                    + entregaB.getId() + "\"]}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String loteB = JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(post("/api/entregador/lotes/" + loteB + "/iniciar")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/entregador/entregas/{id}/status")
    class StatusEntregaTest {

        @Test
        @DisplayName("ENTREGUE e FALHA encerram o lote e devolvem o entregador a ONLINE")
        void deveConcluirLote() throws Exception {
            Entrega primeira = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            Entrega segunda = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            UUID loteId = despachar(motoboy, primeira, segunda);
            iniciar(loteId);

            MvcResult entregue = mockMvc.perform(post("/api/entregador/entregas/" + primeira.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoStatus("ENTREGUE", null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("ENTREGUE")))
                    .andExpect(jsonPath("$.finalizadaEm").exists())
                    .andExpect(jsonPath("$.valorRepasse").exists())
                    .andExpect(jsonPath("$.distanciaKm").exists())
                    .andReturn();
            // T012: o valor congelado visto pelo entregador e o mesmo do painel.
            String corpoEntregue = entregue.getResponse().getContentAsString();
            mockMvc.perform(get("/api/entregas/" + primeira.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valorRepasse").value(
                            (Object) JsonPath.read(corpoEntregue, "$.valorRepasse")))
                    .andExpect(jsonPath("$.distanciaKm").value(
                            (Object) JsonPath.read(corpoEntregue, "$.distanciaKm")));

            mockMvc.perform(post("/api/entregador/entregas/" + segunda.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoStatus("FALHA", "Cliente ausente")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("FALHA")))
                    .andExpect(jsonPath("$.motivoFalha", is("Cliente ausente")));

            mockMvc.perform(get("/api/lotes/" + loteId)
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CONCLUIDO")));
            mockMvc.perform(get("/api/entregador/me")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(jsonPath("$.statusDisponibilidade", is("ONLINE")));
            mockMvc.perform(get("/api/entregador/lote-ativo")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isNoContent());

            List<EntregaStatusHistorico> historico =
                    historicoRepository.findByEntregaIdOrderByRegistradoEmAsc(segunda.getId());
            EntregaStatusHistorico falha = historico.stream()
                    .filter(registro -> registro.getStatusNovo() == StatusEntrega.FALHA)
                    .findFirst()
                    .orElseThrow();
            assertEquals(StatusEntrega.EM_ROTA, falha.getStatusAnterior());
            assertNotNull(falha.getCoordenada());

            verify(publicador, atLeastOnce()).publicarEntrega(eq(loja.getId()), eq("STATUS_ALTERADO"), any());
            verify(publicador, atLeastOnce()).publicarFrota(eq(loja.getId()), eq("DISPONIBILIDADE_ALTERADA"), any());
        }

        @Test
        @DisplayName("FALHA sem motivo: 422")
        void deveExigirMotivoFalha() throws Exception {
            Entrega entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            iniciar(despachar(motoboy, entrega));

            mockMvc.perform(post("/api/entregador/entregas/" + entrega.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoStatus("FALHA", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("REGRA_NEGOCIO_VIOLADA")));
        }

        @Test
        @DisplayName("ENTREGUE antes de iniciar o percurso: 422 TRANSICAO_ESTADO_INVALIDA")
        void deveExigirEmRota() throws Exception {
            Entrega entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            despachar(motoboy, entrega);

            mockMvc.perform(post("/api/entregador/entregas/" + entrega.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoStatus("ENTREGUE", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("TRANSICAO_ESTADO_INVALIDA")));
        }

        @Test
        @DisplayName("Status fora de ENTREGUE/FALHA: 422 TRANSICAO_ESTADO_INVALIDA")
        void deveRecusarOutroStatus() throws Exception {
            Entrega entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            iniciar(despachar(motoboy, entrega));

            mockMvc.perform(post("/api/entregador/entregas/" + entrega.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoStatus("CANCELADA", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("TRANSICAO_ESTADO_INVALIDA")));
        }

        @Test
        @DisplayName("Entrega de outro entregador: 404, sem alterar")
        void deveRecusarEntregaAlheia() throws Exception {
            Entrega alheia = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            UUID loteAlheio = despachar(outroMotoboy, alheia);
            mockMvc.perform(post("/api/entregador/lotes/" + loteAlheio + "/iniciar")
                            .header(HttpHeaders.AUTHORIZATION, bearerOutro))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/entregador/entregas/" + alheia.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoStatus("ENTREGUE", null)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo", is("RECURSO_NAO_ENCONTRADO")));

            assertEquals(StatusEntrega.EM_ROTA, entregaRepository.findById(alheia.getId()).orElseThrow().getStatus());
        }

        @Test
        @DisplayName("Entrega fora de lote: 404")
        void deveRecusarEntregaSemLote() throws Exception {
            Entrega solta = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);

            mockMvc.perform(post("/api/entregador/entregas/" + solta.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoStatus("ENTREGUE", null)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/entregador/entregas/{id}/contestar")
    class Contestar {

        @Test
        @DisplayName("Com caixa aberto: 200 e entrega em revisao")
        void deveContestar() throws Exception {
            cenario.caixa(loja);
            Entrega entrega = entregaConcluida();

            mockMvc.perform(post("/api/entregador/entregas/" + entrega.getId() + "/contestar")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pendenteRevisao", is(true)))
                    .andExpect(jsonPath("$.motivoRevisao", is("CONTESTACAO_ENTREGADOR")));
        }

        @Test
        @DisplayName("Entrega ja em revisao: 422 (UC08 E1)")
        void deveRecusarJaEmRevisao() throws Exception {
            cenario.caixa(loja);
            Entrega entrega = entregaConcluida();
            mockMvc.perform(post("/api/entregador/entregas/" + entrega.getId() + "/contestar")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/entregador/entregas/" + entrega.getId() + "/contestar")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("REGRA_NEGOCIO_VIOLADA")));
        }

        @Test
        @DisplayName("Caixa fechado: 422 CAIXA_FECHADO (UC08 E2)")
        void deveRecusarCaixaFechado() throws Exception {
            cenario.caixa(loja, HorarioLocal.hoje(), StatusCaixa.FECHADO);
            Entrega entrega = entregaConcluida();

            mockMvc.perform(post("/api/entregador/entregas/" + entrega.getId() + "/contestar")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("CAIXA_FECHADO")));
        }

        @Test
        @DisplayName("Entrega de outro entregador: 404")
        void deveRecusarEntregaAlheia() throws Exception {
            cenario.caixa(loja);
            Entrega entrega = entregaConcluida();

            mockMvc.perform(post("/api/entregador/entregas/" + entrega.getId() + "/contestar")
                            .header(HttpHeaders.AUTHORIZATION, bearerOutro))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/entregador/acerto")
    class Acerto {

        @Test
        @DisplayName("Sem periodo: dia atual, com status do caixa ABERTO")
        void deveDevolverAcertoDoDia() throws Exception {
            cenario.caixa(loja);
            Entrega entrega = entregaConcluida();

            mockMvc.perform(get("/api/entregador/acerto")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.extrato.motoboyId", is(motoboy.getId().toString())))
                    .andExpect(jsonPath("$.extrato.linhas", hasSize(1)))
                    .andExpect(jsonPath("$.extrato.linhas[0].entregaId", is(entrega.getId().toString())))
                    .andExpect(jsonPath("$.dataReferenciaCaixa", is(HorarioLocal.hoje().toString())))
                    .andExpect(jsonPath("$.statusCaixa", is("ABERTO")))
                    .andExpect(jsonPath("$.contestacaoPermitida", is(true)));
        }

        @Test
        @DisplayName("Entregas de outro entregador nao entram no acerto")
        void naoDeveMostrarEntregasAlheias() throws Exception {
            cenario.caixa(loja);
            entregaConcluida();

            mockMvc.perform(get("/api/entregador/acerto")
                            .header(HttpHeaders.AUTHORIZATION, bearerOutro))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.extrato.motoboyId", is(outroMotoboy.getId().toString())))
                    .andExpect(jsonPath("$.extrato.linhas", hasSize(0)));
        }

        @Test
        @DisplayName("Caixa fechado: statusCaixa FECHADO e contestacao indisponivel")
        void deveIndicarCaixaFechado() throws Exception {
            cenario.caixa(loja, HorarioLocal.hoje(), StatusCaixa.FECHADO);

            mockMvc.perform(get("/api/entregador/acerto")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCaixa", is("FECHADO")))
                    .andExpect(jsonPath("$.contestacaoPermitida", is(false)));
        }

        @Test
        @DisplayName("Sem caixa no dia: statusCaixa nulo")
        void deveIndicarSemCaixa() throws Exception {
            mockMvc.perform(get("/api/entregador/acerto")
                            .param("inicio", "2026-01-10T00:00:00")
                            .param("fim", "2026-01-10T23:59:59")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dataReferenciaCaixa", is("2026-01-10")))
                    .andExpect(jsonPath("$.statusCaixa", nullValue()))
                    .andExpect(jsonPath("$.contestacaoPermitida", is(false)));
        }

        @Test
        @DisplayName("So inicio informado: 400 PARAMETRO_INVALIDO")
        void deveExigirInicioEFim() throws Exception {
            mockMvc.perform(get("/api/entregador/acerto")
                            .param("inicio", "2026-01-10T00:00:00")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("PARAMETRO_INVALIDO")));
        }

        @Test
        @DisplayName("Periodo com mais de um dia: 400 PARAMETRO_INVALIDO")
        void deveRecusarVariosDias() throws Exception {
            mockMvc.perform(get("/api/entregador/acerto")
                            .param("inicio", "2026-01-10T00:00:00")
                            .param("fim", "2026-01-11T23:59:59")
                            .header(HttpHeaders.AUTHORIZATION, bearerEntregador))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("PARAMETRO_INVALIDO")));
        }
    }
}
