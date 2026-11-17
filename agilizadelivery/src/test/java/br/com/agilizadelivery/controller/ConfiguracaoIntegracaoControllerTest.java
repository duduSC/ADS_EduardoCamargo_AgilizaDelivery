package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;

/**
 * Testes de integracao do ConfiguracaoIntegracaoController (T008).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConfiguracaoIntegracaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioWeb cenario;

    private Estabelecimento loja;
    private Usuario admin;
    private String bearer;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        admin = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);
        bearer = cenario.bearer(admin);
    }

    @Nested
    @DisplayName("GET /api/integracoes")
    class Listar {

        @Test
        @DisplayName("Deve listar configuracoes (inicialmente vazio)")
        void deveListarVazio() throws Exception {
            mockMvc.perform(get("/api/integracoes")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PUT /api/integracoes")
    class Salvar {

        @Test
        @DisplayName("Deve salvar configuracao de integracao")
        void deveSalvar() throws Exception {
            mockMvc.perform(put("/api/integracoes")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"provedor": "IFOOD", "merchantId": "merchant-123"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.provedor", is("IFOOD")));
        }

        @Test
        @DisplayName("Provedor vazio: 400")
        void deveRecusarProvedorVazio() throws Exception {
            mockMvc.perform(put("/api/integracoes")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"provedor": "", "merchantId": "merchant-123"}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Seguranca")
    class Seguranca {

        @Test
        @DisplayName("OPERADOR: 403")
        void deveNegar403ParaOperador() throws Exception {
            Usuario operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);

            mockMvc.perform(get("/api/integracoes")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(operador)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sem token: 401")
        void deveExigir401SemToken() throws Exception {
            mockMvc.perform(get("/api/integracoes"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        }
    }
}
