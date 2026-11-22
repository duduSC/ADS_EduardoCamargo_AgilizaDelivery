package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Testes de integracao do CaixaController (T007).
 *
 * Cobre: abrir, consultar atual, historico, fechar, reabrir,
 * autorizacao por perfil, isolamento entre tenants, erros 4xx.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaixaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioWeb cenario;

    private Estabelecimento loja;
    private Usuario operador;
    private String bearer;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        bearer = cenario.bearer(operador);
    }

    @Nested
    @DisplayName("POST /api/caixas")
    class AbrirCaixa {

        @Test
        @DisplayName("Deve abrir caixa para hoje")
        void deveAbrirCaixa() throws Exception {
            mockMvc.perform(post("/api/caixas")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dataReferencia": "2026-11-13"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.status", is("ABERTO")))
                    .andExpect(jsonPath("$.dataReferencia", is("2026-11-13")));
        }

        @Test
        @DisplayName("Caixa ja aberto no mesmo dia: 409 CAIXA_JA_ABERTO")
        void deveRecusarCaixaDuplicado() throws Exception {
            cenario.caixa(loja, LocalDate.of(2026, 11, 13), StatusCaixa.ABERTO);

            mockMvc.perform(post("/api/caixas")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dataReferencia": "2026-11-13"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo", is("CAIXA_JA_ABERTO")));
        }

        @Test
        @DisplayName("Sem dataReferencia: 400 DADOS_INVALIDOS")
        void deveExigirDataReferencia() throws Exception {
            mockMvc.perform(post("/api/caixas")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("DADOS_INVALIDOS")));
        }
    }

    @Nested
    @DisplayName("GET /api/caixas/atual")
    class CaixaAtual {

        @Test
        @DisplayName("Deve retornar caixa do dia")
        void deveRetornarCaixaDoDia() throws Exception {
            cenario.caixa(loja, HorarioLocal.hoje(), StatusCaixa.ABERTO);

            mockMvc.perform(get("/api/caixas/atual")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("ABERTO")));
        }

        @Test
        @DisplayName("Sem caixa aberto: 404")
        void deveRetornar404SemCaixa() throws Exception {
            mockMvc.perform(get("/api/caixas/atual")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/caixas")
    class ListarCaixas {

        @Test
        @DisplayName("Lista por periodo")
        void deveListarPorPeriodo() throws Exception {
            cenario.caixa(loja, LocalDate.of(2026, 11, 10), StatusCaixa.FECHADO);
            cenario.caixa(loja, LocalDate.of(2026, 11, 11), StatusCaixa.FECHADO);

            mockMvc.perform(get("/api/caixas?dataInicio=2026-11-09&dataFim=2026-11-12")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }
    }

    @Nested
    @DisplayName("POST /api/caixas/{id}/fechar")
    class FecharCaixa {

        @Test
        @DisplayName("Deve fechar caixa aberto")
        void deveFecharCaixa() throws Exception {
            Caixa caixa = cenario.caixa(loja);

            mockMvc.perform(post("/api/caixas/" + caixa.getId() + "/fechar")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("FECHADO")));
        }

        @Test
        @DisplayName("Caixa ja fechado: 422 CAIXA_FECHADO")
        void deveRecusarFecharCaixaJaFechado() throws Exception {
            Caixa caixa = cenario.caixa(loja, HorarioLocal.hoje(), StatusCaixa.FECHADO);

            mockMvc.perform(post("/api/caixas/" + caixa.getId() + "/fechar")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("CAIXA_FECHADO")));
        }
    }

    @Nested
    @DisplayName("POST /api/caixas/{id}/reabrir")
    class ReabrirCaixa {

        @Test
        @DisplayName("ADMIN_RESTAURANTE: deve reabrir caixa fechado")
        void deveReabrirComoAdmin() throws Exception {
            Usuario admin = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);
            Caixa caixa = cenario.caixa(loja, HorarioLocal.hoje(), StatusCaixa.FECHADO);

            mockMvc.perform(post("/api/caixas/" + caixa.getId() + "/reabrir")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"motivo": "Conferencia pendente com entregador"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("REABERTO")));
        }

        @Test
        @DisplayName("OPERADOR: 403 ACESSO_NEGADO (D004)")
        void deveNegar403ParaOperador() throws Exception {
            Caixa caixa = cenario.caixa(loja, HorarioLocal.hoje(), StatusCaixa.FECHADO);

            mockMvc.perform(post("/api/caixas/" + caixa.getId() + "/reabrir")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"motivo": "Conferencia pendente"}
                                    """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sem motivo: 400 DADOS_INVALIDOS")
        void deveExigirMotivo() throws Exception {
            Usuario admin = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);
            Caixa caixa = cenario.caixa(loja, HorarioLocal.hoje(), StatusCaixa.FECHADO);

            mockMvc.perform(post("/api/caixas/" + caixa.getId() + "/reabrir")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"motivo": ""}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("DADOS_INVALIDOS")));
        }

        @Test
        @DisplayName("Caixa aberto: 422 TRANSICAO_ESTADO_INVALIDA")
        void deveRecusarReabrirCaixaAberto() throws Exception {
            Usuario admin = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);
            Caixa caixa = cenario.caixa(loja);

            mockMvc.perform(post("/api/caixas/" + caixa.getId() + "/reabrir")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"motivo": "Teste"}
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("TRANSICAO_ESTADO_INVALIDA")));
        }
    }

    @Nested
    @DisplayName("Autorizacao e seguranca")
    class Seguranca {

        @Test
        @DisplayName("Sem token: 401 NAO_AUTENTICADO")
        void deveExigir401SemToken() throws Exception {
            mockMvc.perform(get("/api/caixas/atual"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        }

        @Test
        @DisplayName("ENTREGADOR: 403 ACESSO_NEGADO")
        void deveNegar403ParaEntregador() throws Exception {
            var motoboy = cenario.motoboy(loja);

            mockMvc.perform(get("/api/caixas/atual")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(motoboy.getUsuario())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Isolamento de tenant: operador de A nao ve caixa de B")
        void deveIsolarTenants() throws Exception {
            Estabelecimento lojaB = cenario.estabelecimento();
            cenario.caixa(lojaB);

            mockMvc.perform(get("/api/caixas/atual")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNotFound());
        }
    }
}
