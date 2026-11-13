package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.hasSize;
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

import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;

/**
 * Testes de integracao do ReciboController (T007).
 *
 * Cobre: gerar, listar, buscar por id, pagar, cancelar,
 * regras de negocio (cancelar com caixa fechado), autorizacao, tenant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReciboControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioWeb cenario;

    private Estabelecimento loja;
    private Usuario operador;
    private String bearer;
    private Motoboy motoboy;
    private Caixa caixa;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        bearer = cenario.bearer(operador);
        motoboy = cenario.motoboy(loja);
        caixa = cenario.caixa(loja);
    }

    @Nested
    @DisplayName("POST /api/recibos")
    class GerarRecibo {

        @Test
        @DisplayName("Sem entregas no periodo: 422")
        void deveRecusarSemEntregas() throws Exception {
            mockMvc.perform(post("/api/recibos")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "motoboyId": "%s",
                                      "periodoInicio": "2026-11-13T00:00:00",
                                      "periodoFim": "2026-11-13T23:59:59"
                                    }
                                    """.formatted(motoboy.getId())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("Periodo invalido (fim antes do inicio): 422")
        void deveRecusarPeriodoInvalido() throws Exception {
            mockMvc.perform(post("/api/recibos")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "motoboyId": "%s",
                                      "periodoInicio": "2026-11-13T23:59:59",
                                      "periodoFim": "2026-11-13T00:00:00"
                                    }
                                    """.formatted(motoboy.getId())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("Sem motoboyId: 400 DADOS_INVALIDOS")
        void deveExigirMotoboyId() throws Exception {
            mockMvc.perform(post("/api/recibos")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "periodoInicio": "2026-11-13T00:00:00",
                                      "periodoFim": "2026-11-13T23:59:59"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("DADOS_INVALIDOS")));
        }
    }

    @Nested
    @DisplayName("GET /api/recibos")
    class ListarRecibos {

        @Test
        @DisplayName("Listar por caixaId")
        void deveListarPorCaixa() throws Exception {
            cenario.recibo(loja, motoboy, caixa, operador);

            mockMvc.perform(get("/api/recibos?caixaId=" + caixa.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        @DisplayName("Listar por motoboyId")
        void deveListarPorMotoboy() throws Exception {
            cenario.recibo(loja, motoboy, caixa, operador);

            mockMvc.perform(get("/api/recibos?motoboyId=" + motoboy.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("GET /api/recibos/{id}")
    class BuscarReciboPorId {

        @Test
        @DisplayName("Deve devolver o recibo pelo id")
        void deveDevolver() throws Exception {
            Recibo recibo = cenario.recibo(loja, motoboy, caixa, operador);

            mockMvc.perform(get("/api/recibos/" + recibo.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(recibo.getId().toString())));
        }

        @Test
        @DisplayName("Recibo de outro estabelecimento: 404")
        void deveNegarReciboDeOutroTenant() throws Exception {
            Estabelecimento outra = cenario.estabelecimento();
            Usuario outroOperador = cenario.usuario(outra, PerfilUsuario.OPERADOR);
            Motoboy outroMotoboy = cenario.motoboy(outra);
            Caixa outroCaixa = cenario.caixa(outra);
            Recibo alheio = cenario.recibo(outra, outroMotoboy, outroCaixa, outroOperador);

            mockMvc.perform(get("/api/recibos/" + alheio.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/recibos/{id}/pagar")
    class PagarRecibo {

        @Test
        @DisplayName("Deve marcar recibo como pago")
        void deveMarcarComoPago() throws Exception {
            Recibo recibo = cenario.recibo(loja, motoboy, caixa, operador);

            mockMvc.perform(post("/api/recibos/" + recibo.getId() + "/pagar")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("PAGO")))
                    .andExpect(jsonPath("$.pagoEm", notNullValue()));
        }
    }

    @Nested
    @DisplayName("POST /api/recibos/{id}/cancelar")
    class CancelarRecibo {

        @Test
        @DisplayName("Deve cancelar recibo com caixa aberto")
        void deveCancelarComCaixaAberto() throws Exception {
            Recibo recibo = cenario.recibo(loja, motoboy, caixa, operador);

            mockMvc.perform(post("/api/recibos/" + recibo.getId() + "/cancelar")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"motivo": "Valor divergente no conferencia"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CANCELADO")));
        }

        @Test
        @DisplayName("Cancelar com caixa fechado: 422 RECIBO_INALTERAVEL")
        void deveRecusarCancelarComCaixaFechado() throws Exception {
            Caixa caixaFechado = cenario.caixa(loja, java.time.LocalDate.of(2026, 11, 10), StatusCaixa.FECHADO);
            Recibo recibo = cenario.recibo(loja, motoboy, caixaFechado, operador);

            mockMvc.perform(post("/api/recibos/" + recibo.getId() + "/cancelar")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"motivo": "Valor divergente"}
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("RECIBO_INALTERAVEL")));
        }

        @Test
        @DisplayName("Sem motivo: 400 DADOS_INVALIDOS")
        void deveExigirMotivo() throws Exception {
            Recibo recibo = cenario.recibo(loja, motoboy, caixa, operador);

            mockMvc.perform(post("/api/recibos/" + recibo.getId() + "/cancelar")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"motivo": ""}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("DADOS_INVALIDOS")));
        }
    }

    @Nested
    @DisplayName("Autorizacao e seguranca")
    class Seguranca {

        @Test
        @DisplayName("Sem token: 401 NAO_AUTENTICADO")
        void deveExigir401SemToken() throws Exception {
            mockMvc.perform(get("/api/recibos"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        }

        @Test
        @DisplayName("ENTREGADOR: 403")
        void deveNegar403ParaEntregador() throws Exception {
            mockMvc.perform(get("/api/recibos")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(motoboy.getUsuario())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN_RESTAURANTE: 200 (D004)")
        void devePermitirAdminRestaurante() throws Exception {
            Usuario admin = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);
            cenario.recibo(loja, motoboy, caixa, operador);

            mockMvc.perform(get("/api/recibos?caixaId=" + caixa.getId())
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(admin)))
                    .andExpect(status().isOk());
        }
    }
}
