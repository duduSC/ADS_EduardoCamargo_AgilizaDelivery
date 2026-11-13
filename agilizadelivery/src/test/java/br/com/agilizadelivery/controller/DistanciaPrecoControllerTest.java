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
 * Testes de integracao do DistanciaPrecoController (T008).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DistanciaPrecoControllerTest {

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
    @DisplayName("GET /api/faixas-preco")
    class Listar {

        @Test
        @DisplayName("Deve listar faixas ativas (inicialmente vazio)")
        void deveListarVazio() throws Exception {
            mockMvc.perform(get("/api/faixas-preco")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("OPERADOR: 403 (so ADMIN_RESTAURANTE)")
        void deveNegar403ParaOperador() throws Exception {
            Usuario operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);

            mockMvc.perform(get("/api/faixas-preco")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(operador)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/faixas-preco")
    class Salvar {

        @Test
        @DisplayName("Deve salvar tabela de faixas valida")
        void deveSalvarTabela() throws Exception {
            mockMvc.perform(put("/api/faixas-preco")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    [
                                      {"deKm": 0, "ateKm": 3, "valorPago": 8.00, "vigenteDesde": "2026-11-13"},
                                      {"deKm": 3, "ateKm": 6, "valorPago": 12.00, "vigenteDesde": "2026-11-13"}
                                    ]
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Faixa invalida (ateKm < deKm): 422")
        void deveRecusarFaixaInvalida() throws Exception {
            mockMvc.perform(put("/api/faixas-preco")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    [
                                      {"deKm": 5, "ateKm": 3, "valorPago": 8.00, "vigenteDesde": "2026-11-13"}
                                    ]
                                    """))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("Seguranca")
    class Seguranca {

        @Test
        @DisplayName("Sem token: 401")
        void deveExigir401SemToken() throws Exception {
            mockMvc.perform(get("/api/faixas-preco"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        }
    }
}
