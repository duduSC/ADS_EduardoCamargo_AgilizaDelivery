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
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;

/**
 * Testes de integracao do FalhaIntegracaoController (T008).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FalhaIntegracaoControllerTest {

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
    @DisplayName("GET /api/falhas-integracao")
    class Listar {

        @Test
        @DisplayName("Deve listar falhas pendentes (inicialmente vazio)")
        void deveListarVazio() throws Exception {
            mockMvc.perform(get("/api/falhas-integracao")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN_RESTAURANTE: 200 (D004)")
        void devePermitirAdmin() throws Exception {
            Usuario admin = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);

            mockMvc.perform(get("/api/falhas-integracao")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(admin)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Seguranca")
    class Seguranca {

        @Test
        @DisplayName("Sem token: 401")
        void deveExigir401SemToken() throws Exception {
            mockMvc.perform(get("/api/falhas-integracao"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        }

        @Test
        @DisplayName("ENTREGADOR: 403")
        void deveNegar403ParaEntregador() throws Exception {
            var motoboy = cenario.motoboy(loja);

            mockMvc.perform(get("/api/falhas-integracao")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(motoboy.getUsuario())))
                    .andExpect(status().isForbidden());
        }
    }
}
