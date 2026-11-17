package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Testes de integracao do EstabelecimentoController (T008).
 *
 * Cobre: GET/PATCH /api/estabelecimento (do token), CRUD /api/estabelecimentos (ADMIN_GERAL),
 * autorizacao por perfil, CNPJ duplicado, isolamento de tenant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EstabelecimentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioWeb cenario;

    private Estabelecimento loja;
    private Usuario adminRestaurante;
    private String bearerAdmin;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        adminRestaurante = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);
        bearerAdmin = cenario.bearer(adminRestaurante);
    }

    @Nested
    @DisplayName("GET /api/estabelecimento")
    class MeuEstabelecimento {

        @Test
        @DisplayName("OPERADOR: deve ver o proprio estabelecimento")
        void deveRetornarEstabelecimento() throws Exception {
            Usuario operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);

            mockMvc.perform(get("/api/estabelecimento")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(operador)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(loja.getId().toString())));
        }

        @Test
        @DisplayName("ADMIN_RESTAURANTE: 200")
        void devePermitirAdmin() throws Exception {
            mockMvc.perform(get("/api/estabelecimento")
                            .header(HttpHeaders.AUTHORIZATION, bearerAdmin))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PATCH /api/estabelecimento")
    class Editar {

        @Test
        @DisplayName("Deve editar parametros da loja")
        void deveEditarParametros() throws Exception {
            mockMvc.perform(patch("/api/estabelecimento")
                            .header(HttpHeaders.AUTHORIZATION, bearerAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"maxPedidosPorLote": 10, "valorRepasseForaDeArea": 20.00}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.maxPedidosPorLote", is(10)))
                    .andExpect(jsonPath("$.valorRepasseForaDeArea").value(20.00));
        }

        @Test
        @DisplayName("OPERADOR: 403 (so ADMIN_RESTAURANTE)")
        void deveNegarOperador() throws Exception {
            Usuario operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);

            mockMvc.perform(patch("/api/estabelecimento")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(operador))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"maxPedidosPorLote": 10}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("CRUD /api/estabelecimentos (ADMIN_GERAL)")
    class Plataforma {

        private Usuario adminGeral;
        private String bearerGeral;

        @BeforeEach
        void setUpGeral() {
            adminGeral = cenario.usuario(null, PerfilUsuario.ADMIN_GERAL);
            bearerGeral = cenario.bearer(adminGeral);
        }

        @Test
        @DisplayName("Listar todos os restaurantes")
        void deveListarTodos() throws Exception {
            mockMvc.perform(get("/api/estabelecimentos")
                            .header(HttpHeaders.AUTHORIZATION, bearerGeral))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Criar restaurante com administrador inicial")
        void deveCriarRestaurante() throws Exception {
            mockMvc.perform(post("/api/estabelecimentos")
                            .header(HttpHeaders.AUTHORIZATION, bearerGeral)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "razaoSocial": "Pizzaria Teste Ltda",
                                      "nomeFantasia": "Pizzaria Teste",
                                      "cnpj": "11222333000181",
                                      "enderecoCompleto": "Rua B, 200",
                                      "coordenada": {"latitude": -23.55, "longitude": -46.63},
                                      "maxPedidosPorLote": 5,
                                      "administrador": {
                                        "nome": "Admin Teste",
                                        "loginEmail": "admin@pizzariateste.com",
                                        "senha": "senhaforte123"
                                      }
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.status", is("ATIVO")));
        }

        @Test
        @DisplayName("CNPJ duplicado: 409 CNPJ_JA_CADASTRADO")
        void deveRecusarCnpjDuplicado() throws Exception {
            // Usar o CNPJ da loja ja existente
            mockMvc.perform(post("/api/estabelecimentos")
                            .header(HttpHeaders.AUTHORIZATION, bearerGeral)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "razaoSocial": "Outra Loja Ltda",
                                      "nomeFantasia": "Outra Loja",
                                      "cnpj": "%s",
                                      "enderecoCompleto": "Rua C, 300",
                                      "coordenada": {"latitude": -23.55, "longitude": -46.63},
                                      "maxPedidosPorLote": 3,
                                      "administrador": {
                                        "nome": "Admin",
                                        "loginEmail": "admin@outraloja.com",
                                        "senha": "senhaforte123"
                                      }
                                    }
                                    """.formatted(loja.getCnpj())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo", is("CNPJ_JA_CADASTRADO")));
        }

        @Test
        @DisplayName("Buscar por ID")
        void deveBuscarPorId() throws Exception {
            mockMvc.perform(get("/api/estabelecimentos/" + loja.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearerGeral))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(loja.getId().toString())));
        }

        @Test
        @DisplayName("Alterar status para INATIVO")
        void deveInativar() throws Exception {
            mockMvc.perform(patch("/api/estabelecimentos/" + loja.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, bearerGeral)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status": "INATIVO"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("INATIVO")));
        }

        @Test
        @DisplayName("ADMIN_RESTAURANTE: 403 para endpoints de plataforma")
        void deveNegarAdminRestaurante() throws Exception {
            mockMvc.perform(get("/api/estabelecimentos")
                            .header(HttpHeaders.AUTHORIZATION, bearerAdmin))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Seguranca")
    class Seguranca {

        @Test
        @DisplayName("Sem token: 401")
        void deveExigir401SemToken() throws Exception {
            mockMvc.perform(get("/api/estabelecimento"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        }

        @Test
        @DisplayName("ENTREGADOR: 403 para /api/estabelecimento")
        void deveNegar403ParaEntregador() throws Exception {
            var motoboy = cenario.motoboy(loja);

            mockMvc.perform(get("/api/estabelecimento")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(motoboy.getUsuario())))
                    .andExpect(status().isForbidden());
        }
    }
}
