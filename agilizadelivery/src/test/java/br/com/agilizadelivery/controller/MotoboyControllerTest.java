package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;

/**
 * Testes de integracao do MotoboyController (T006).
 *
 * Cobre: listagem (com filtros), criacao, edicao, bloquear/desbloquear,
 * autorizacao por perfil, isolamento entre tenants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MotoboyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioWeb cenario;

    private Estabelecimento loja;
    private Usuario admin;
    private String bearerAdmin;
    private Usuario operador;
    private String bearerOperador;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        admin = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);
        bearerAdmin = cenario.bearer(admin);
        operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        bearerOperador = cenario.bearer(operador);
    }

    @Nested
    @DisplayName("GET /api/motoboys")
    class Listagem {

        @Test
        @DisplayName("Lista todos os entregadores do estabelecimento")
        void deveListarTodos() throws Exception {
            cenario.motoboy(loja);
            cenario.motoboy(loja);

            mockMvc.perform(get("/api/motoboys")
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
        }

        @Test
        @DisplayName("Filtro por statusCadastro=ATIVO")
        void deveListarPorStatusCadastro() throws Exception {
            cenario.motoboy(loja, StatusCadastroMotoboy.ATIVO, StatusDisponibilidadeMotoboy.ONLINE);
            cenario.motoboy(loja, StatusCadastroMotoboy.BLOQUEADO, StatusDisponibilidadeMotoboy.OFFLINE);

            mockMvc.perform(get("/api/motoboys?statusCadastro=ATIVO")
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].statusCadastro", is("ATIVO")));
        }

        @Test
        @DisplayName("Filtro por statusDisponibilidade=ONLINE")
        void deveListarPorStatusDisponibilidade() throws Exception {
            cenario.motoboy(loja, StatusCadastroMotoboy.ATIVO, StatusDisponibilidadeMotoboy.ONLINE);
            cenario.motoboy(loja, StatusCadastroMotoboy.ATIVO, StatusDisponibilidadeMotoboy.OFFLINE);

            mockMvc.perform(get("/api/motoboys?statusDisponibilidade=ONLINE")
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].statusDisponibilidade", is("ONLINE")));
        }
    }

    @Nested
    @DisplayName("POST /api/motoboys")
    class Criar {

        @Test
        @DisplayName("Criar entregador: 201 (so ADMIN_RESTAURANTE)")
        void deveCriar() throws Exception {
            mockMvc.perform(post("/api/motoboys")
                            .header(HttpHeaders.AUTHORIZATION, bearerAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "nome": "Carlos",
                                      "loginEmail": "carlos@teste.com",
                                      "senha": "senha-segura-123",
                                      "cpf": "123.456.789-09",
                                      "telefone": "11999990000",
                                      "placaVeiculo": "ABC1D23"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.nome", is("Carlos")));
        }

        @Test
        @DisplayName("OPERADOR nao pode criar: 403")
        void deveNegar403ParaOperador() throws Exception {
            mockMvc.perform(post("/api/motoboys")
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "nome": "Carlos",
                                      "loginEmail": "carlos2@teste.com",
                                      "senha": "senha-segura-123",
                                      "cpf": "123.456.789-09",
                                      "telefone": "11999990000"
                                    }
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/motoboys/{id}")
    class Editar {

        @Test
        @DisplayName("Editar telefone e placa: 200")
        void deveEditar() throws Exception {
            Motoboy m = cenario.motoboy(loja);

            mockMvc.perform(put("/api/motoboys/" + m.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearerAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"telefone": "11888880000", "placaVeiculo": "XYZ9A99"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.telefone", is("11888880000")))
                    .andExpect(jsonPath("$.placaVeiculo", is("XYZ9A99")));
        }

        @Test
        @DisplayName("OPERADOR nao pode editar: 403")
        void deveNegar403ParaOperadorEditar() throws Exception {
            Motoboy m = cenario.motoboy(loja);

            mockMvc.perform(put("/api/motoboys/" + m.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"telefone": "11888880000"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/motoboys/{id}/bloquear e /desbloquear")
    class BloquearDesbloquear {

        @Test
        @DisplayName("Bloquear e depois desbloquear")
        void deveBloquearEDesbloquear() throws Exception {
            Motoboy m = cenario.motoboy(loja, StatusCadastroMotoboy.ATIVO,
                    StatusDisponibilidadeMotoboy.OFFLINE);

            mockMvc.perform(post("/api/motoboys/" + m.getId() + "/bloquear")
                            .header(HttpHeaders.AUTHORIZATION, bearerAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCadastro", is("BLOQUEADO")));

            mockMvc.perform(post("/api/motoboys/" + m.getId() + "/desbloquear")
                            .header(HttpHeaders.AUTHORIZATION, bearerAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCadastro", is("ATIVO")));
        }
    }

    @Nested
    @DisplayName("Autorizacao e seguranca")
    class Seguranca {

        @Test
        @DisplayName("Sem token: 401")
        void deveExigir401() throws Exception {
            mockMvc.perform(get("/api/motoboys"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ENTREGADOR nao pode listar: 403")
        void deveNegar403ParaEntregador() throws Exception {
            Motoboy m = cenario.motoboy(loja);
            mockMvc.perform(get("/api/motoboys")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(m.getUsuario())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Isolamento: operador de A nao ve motoboys de B")
        void deveIsolarTenants() throws Exception {
            Estabelecimento lojaB = cenario.estabelecimento();
            cenario.motoboy(lojaB);

            mockMvc.perform(get("/api/motoboys")
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }
}
