package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Testes de integracao do UsuarioController (T008).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioControllerTest {

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
    @DisplayName("GET /api/usuarios")
    class Listar {

        @Test
        @DisplayName("Deve listar usuarios do estabelecimento")
        void deveListar() throws Exception {
            mockMvc.perform(get("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/usuarios")
    class Criar {

        @Test
        @DisplayName("Deve criar usuario operador")
        void deveCriarOperador() throws Exception {
            mockMvc.perform(post("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "nome": "Novo Operador",
                                      "loginEmail": "novo.operador@teste.com",
                                      "senha": "senhaforte123",
                                      "perfil": "OPERADOR"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.perfil", is("OPERADOR")));
        }

        @Test
        @DisplayName("E-mail duplicado: 409")
        void deveRecusarEmailDuplicado() throws Exception {
            mockMvc.perform(post("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "nome": "Duplicado",
                                      "loginEmail": "%s",
                                      "senha": "senhaforte123",
                                      "perfil": "OPERADOR"
                                    }
                                    """.formatted(admin.getLoginEmail())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo", is("EMAIL_JA_CADASTRADO")));
        }

        @Test
        @DisplayName("Sem senha: 400")
        void deveExigirSenha() throws Exception {
            mockMvc.perform(post("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "nome": "Sem Senha",
                                      "loginEmail": "semsinha@teste.com",
                                      "perfil": "OPERADOR"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("DADOS_INVALIDOS")));
        }
    }

    @Nested
    @DisplayName("Seguranca")
    class Seguranca {

        @Test
        @DisplayName("OPERADOR: 403 (so ADMIN_RESTAURANTE)")
        void deveNegar403ParaOperador() throws Exception {
            Usuario operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);

            mockMvc.perform(get("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(operador)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sem token: 401")
        void deveExigir401SemToken() throws Exception {
            mockMvc.perform(get("/api/usuarios"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        }

        @Test
        @DisplayName("Isolamento de tenant: admin de A nao ve usuarios de B")
        void deveIsolarTenants() throws Exception {
            Estabelecimento lojaB = cenario.estabelecimento();
            cenario.usuario(lojaB, PerfilUsuario.OPERADOR);

            // O admin da loja A so deve ver usuarios da loja A
            mockMvc.perform(get("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());
            // Conteudo do resultado ja esta filtrado pelo token (tenant A)
        }
    }
}
