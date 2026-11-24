package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;

/**
 * Testes de integracao do AcertoController (T007).
 *
 * Cobre: extrato do acerto, validacao de periodo, autorizacao, tenant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AcertoControllerTest {

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
    @DisplayName("GET /api/acertos")
    class Extrato {

        @Test
        @DisplayName("Deve retornar extrato vazio para periodo sem entregas")
        void deveRetornarExtratoVazio() throws Exception {
            mockMvc.perform(get("/api/acertos")
                            .param("motoboyId", motoboy.getId().toString())
                            .param("inicio", "2026-11-13T00:00:00")
                            .param("fim", "2026-11-13T23:59:59")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.motoboyId", is(motoboy.getId().toString())))
                    .andExpect(jsonPath("$.totalDevido").value(0))
                    .andExpect(jsonPath("$.totalConsolidado").value(0))
                    .andExpect(jsonPath("$.totalEmRevisao").value(0));
        }

        @Test
        @DisplayName("Periodo invalido (fim antes do inicio): 422")
        void deveRecusarPeriodoInvalido() throws Exception {
            mockMvc.perform(get("/api/acertos")
                            .param("motoboyId", motoboy.getId().toString())
                            .param("inicio", "2026-11-13T23:59:59")
                            .param("fim", "2026-11-13T00:00:00")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("Sem motoboyId: 400")
        void deveExigirMotoboyId() throws Exception {
            mockMvc.perform(get("/api/acertos")
                            .param("inicio", "2026-11-13T00:00:00")
                            .param("fim", "2026-11-13T23:59:59")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Autorizacao e seguranca")
    class Seguranca {

        @Test
        @DisplayName("Sem token: 401 NAO_AUTENTICADO")
        void deveExigir401SemToken() throws Exception {
            mockMvc.perform(get("/api/acertos")
                            .param("motoboyId", motoboy.getId().toString())
                            .param("inicio", "2026-11-13T00:00:00")
                            .param("fim", "2026-11-13T23:59:59"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        }

        @Test
        @DisplayName("ENTREGADOR: 403")
        void deveNegar403ParaEntregador() throws Exception {
            mockMvc.perform(get("/api/acertos")
                            .param("motoboyId", motoboy.getId().toString())
                            .param("inicio", "2026-11-13T00:00:00")
                            .param("fim", "2026-11-13T23:59:59")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(motoboy.getUsuario())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN_RESTAURANTE: 200 (D004)")
        void devePermitirAdminRestaurante() throws Exception {
            Usuario admin = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);

            mockMvc.perform(get("/api/acertos")
                            .param("motoboyId", motoboy.getId().toString())
                            .param("inicio", "2026-11-13T00:00:00")
                            .param("fim", "2026-11-13T23:59:59")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(admin)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Isolamento de tenant: operador de A nao consulta motoboy de B")
        void deveIsolarTenants() throws Exception {
            Estabelecimento lojaB = cenario.estabelecimento();
            Motoboy motoboyB = cenario.motoboy(lojaB);

            mockMvc.perform(get("/api/acertos")
                            .param("motoboyId", motoboyB.getId().toString())
                            .param("inicio", "2026-11-13T00:00:00")
                            .param("fim", "2026-11-13T23:59:59")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNotFound());
        }
    }
}
