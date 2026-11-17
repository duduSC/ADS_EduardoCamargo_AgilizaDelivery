package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

/**
 * Testes de integracao do LoteEntregaController (T006).
 *
 * Cobre: despacho, devolucao, listagem, rota, erros de validacao,
 * autorizacao por perfil, isolamento entre tenants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoteEntregaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioWeb cenario;

    private Estabelecimento loja;
    private Usuario operador;
    private String bearer;
    private Motoboy motoboy;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        bearer = cenario.bearer(operador);
        motoboy = cenario.motoboy(loja);
    }

    @Nested
    @DisplayName("POST /api/lotes")
    class Despachar {

        @Test
        @DisplayName("Despacho com sucesso: 201 e lote DESPACHADO")
        void deveDespacharLote() throws Exception {
            Entrega a = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            Entrega b = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);

            mockMvc.perform(post("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "motoboyId": "%s",
                                      "entregaIds": ["%s", "%s"]
                                    }
                                    """.formatted(motoboy.getId(), a.getId(), b.getId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.status", is("DESPACHADO")))
                    .andExpect(jsonPath("$.qtdEntregas", is(2)));
        }

        @Test
        @DisplayName("Limite de lote excedido: 400")
        void deveRecusarLimiteExcedido() throws Exception {
            Estabelecimento lojaComLimite = cenario.estabelecimento(
                    br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento.ATIVO, (short) 1);
            Usuario op = cenario.usuario(lojaComLimite, PerfilUsuario.OPERADOR);
            Motoboy mt = cenario.motoboy(lojaComLimite);
            Entrega a = cenario.entrega(lojaComLimite, StatusEntrega.AGUARDANDO_DESPACHO);
            Entrega b = cenario.entrega(lojaComLimite, StatusEntrega.AGUARDANDO_DESPACHO);

            mockMvc.perform(post("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(op))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "motoboyId": "%s",
                                      "entregaIds": ["%s", "%s"]
                                    }
                                    """.formatted(mt.getId(), a.getId(), b.getId())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("LIMITE_LOTE_EXCEDIDO")));
        }

        @Test
        @DisplayName("Entregador BLOQUEADO: 400 MOTOBOY_INDISPONIVEL")
        void deveRecusarEntregadorBloqueado() throws Exception {
            Motoboy bloqueado = cenario.motoboy(loja, StatusCadastroMotoboy.BLOQUEADO,
                    StatusDisponibilidadeMotoboy.OFFLINE);
            Entrega a = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);

            mockMvc.perform(post("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "motoboyId": "%s",
                                      "entregaIds": ["%s"]
                                    }
                                    """.formatted(bloqueado.getId(), a.getId())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("MOTOBOY_INDISPONIVEL")));
        }

        @Test
        @DisplayName("Entrega com revisao pendente: 400 ENTREGA_INAPTA_LOTE")
        void deveRecusarEntregaComRevisao() throws Exception {
            Entrega a = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            a.setPendenteRevisao(true);

            mockMvc.perform(post("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "motoboyId": "%s",
                                      "entregaIds": ["%s"]
                                    }
                                    """.formatted(motoboy.getId(), a.getId())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("ENTREGA_INAPTA_LOTE")));
        }
    }

    @Nested
    @DisplayName("POST /api/lotes/{id}/devolver")
    class Devolver {

        @Test
        @DisplayName("Devolucao com sucesso: lote CANCELADO, entregas voltam a AGUARDANDO_DESPACHO")
        void deveDevolverLote() throws Exception {
            Entrega a = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);

            // Despachar primeiro
            String resposta = mockMvc.perform(post("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "motoboyId": "%s",
                                      "entregaIds": ["%s"]
                                    }
                                    """.formatted(motoboy.getId(), a.getId())))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String loteId = com.jayway.jsonpath.JsonPath.read(resposta, "$.id");

            mockMvc.perform(post("/api/lotes/" + loteId + "/devolver")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CANCELADO")));
        }

        @Test
        @DisplayName("Devolver lote inexistente: 404")
        void deveRetornar404ParaLoteInexistente() throws Exception {
            mockMvc.perform(post("/api/lotes/" + UUID.randomUUID() + "/devolver")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo", is("RECURSO_NAO_ENCONTRADO")));
        }
    }

    @Nested
    @DisplayName("GET /api/lotes e GET /api/lotes/{id}/rota")
    class Consultas {

        @Test
        @DisplayName("Lista lotes despachados (padrao)")
        void deveListarLotesDespachados() throws Exception {
            Entrega a = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            mockMvc.perform(post("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "motoboyId": "%s",
                                      "entregaIds": ["%s"]
                                    }
                                    """.formatted(motoboy.getId(), a.getId())))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].status", is("DESPACHADO")));
        }

        @Test
        @DisplayName("Rota do lote com paradas ordenadas")
        void deveRetornarRotaDoLote() throws Exception {
            Entrega a = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            String resposta = mockMvc.perform(post("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "motoboyId": "%s",
                                      "entregaIds": ["%s"]
                                    }
                                    """.formatted(motoboy.getId(), a.getId())))
                    .andReturn().getResponse().getContentAsString();

            String loteId = com.jayway.jsonpath.JsonPath.read(resposta, "$.id");

            mockMvc.perform(get("/api/lotes/" + loteId + "/rota")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loteId", is(loteId)))
                    .andExpect(jsonPath("$.paradas", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("Autorizacao e seguranca")
    class Seguranca {

        @Test
        @DisplayName("Sem token: 401")
        void deveExigir401() throws Exception {
            mockMvc.perform(get("/api/lotes"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ENTREGADOR: 403")
        void deveNegar403ParaEntregador() throws Exception {
            mockMvc.perform(get("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(motoboy.getUsuario())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN_RESTAURANTE: 200 (D004)")
        void devePermitirAdminRestaurante() throws Exception {
            Usuario admin = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);
            mockMvc.perform(get("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(admin)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Isolamento de tenant: operador de A nao ve lotes de B")
        void deveIsolarTenants() throws Exception {
            Estabelecimento lojaB = cenario.estabelecimento();
            Motoboy motoboyB = cenario.motoboy(lojaB);
            Entrega e = cenario.entrega(lojaB, StatusEntrega.AGUARDANDO_DESPACHO);
            Usuario opB = cenario.usuario(lojaB, PerfilUsuario.OPERADOR);

            mockMvc.perform(post("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(opB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "motoboyId": "%s",
                                      "entregaIds": ["%s"]
                                    }
                                    """.formatted(motoboyB.getId(), e.getId())))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/lotes")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }
}
