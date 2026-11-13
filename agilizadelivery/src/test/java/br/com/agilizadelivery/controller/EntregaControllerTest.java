package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

/**
 * Testes de integracao do EntregaController (T005). Roda contra H2.
 *
 * Cobre: listagem (com e sem filtro), criacao avulsa, operacoes de
 * estado, autorizacao por perfil, isolamento entre tenants, erros 4xx.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EntregaControllerTest {

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
    @DisplayName("GET /api/entregas")
    class Listagem {

        @Test
        @DisplayName("Sem filtro: devolve pendentes de despacho")
        void deveListarPendentesDeDespacho() throws Exception {
            cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            cenario.entrega(loja, StatusEntrega.ENTREGUE);

            mockMvc.perform(get("/api/entregas")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].status", is("AGUARDANDO_DESPACHO")));
        }

        @Test
        @DisplayName("Com status: filtra por status informado")
        void deveListarPorStatus() throws Exception {
            cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            cenario.entrega(loja, StatusEntrega.ENTREGUE);

            mockMvc.perform(get("/api/entregas?status=ENTREGUE")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].status", is("ENTREGUE")));
        }

        @Test
        @DisplayName("Com pendenteRevisao=true: fila de revisao")
        void deveListarPendentesDeRevisao() throws Exception {
            Entrega normal = cenario.entrega(loja, StatusEntrega.ENTREGUE);
            Entrega pendente = cenario.entrega(loja, StatusEntrega.ENTREGUE);
            pendente.setPendenteRevisao(true);

            mockMvc.perform(get("/api/entregas?pendenteRevisao=true")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id", is(pendente.getId().toString())));
        }
    }

    @Nested
    @DisplayName("GET /api/entregas/{id}")
    class BuscarPorId {

        @Test
        @DisplayName("Deve devolver a entrega pelo id")
        void deveDevolver() throws Exception {
            Entrega entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);

            mockMvc.perform(get("/api/entregas/" + entrega.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(entrega.getId().toString())));
        }

        @Test
        @DisplayName("Entrega de outro estabelecimento: 404 sem revelar existencia")
        void deveNegarEntregaDeOutroTenant() throws Exception {
            Estabelecimento outra = cenario.estabelecimento();
            Entrega alheia = cenario.entrega(outra, StatusEntrega.AGUARDANDO_DESPACHO);

            mockMvc.perform(get("/api/entregas/" + alheia.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo", is("RECURSO_NAO_ENCONTRADO")));
        }
    }

    @Nested
    @DisplayName("POST /api/entregas")
    class CriarAvulsa {

        @Test
        @DisplayName("Deve criar entrega avulsa com status AGUARDANDO_DESPACHO")
        void deveCriarAvulsa() throws Exception {
            mockMvc.perform(post("/api/entregas")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "nomeCliente": "Maria",
                                      "enderecoCompleto": "Rua A, 100 - Centro",
                                      "coordenada": {"latitude": -23.55, "longitude": -46.63}
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.status", is("AGUARDANDO_DESPACHO")))
                    .andExpect(jsonPath("$.nomeCliente", is("Maria")));
        }

        @Test
        @DisplayName("Sem nome do cliente: 400 DADOS_INVALIDOS")
        void deveRecusarSemNome() throws Exception {
            mockMvc.perform(post("/api/entregas")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"enderecoCompleto": "Rua A, 100"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("DADOS_INVALIDOS")));
        }
    }

    @Nested
    @DisplayName("POST /api/entregas/{id}/pronto")
    class MarcarPronta {

        @Test
        @DisplayName("Deve marcar como pronta para despacho")
        void deveMarcarPronta() throws Exception {
            Entrega entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);

            mockMvc.perform(post("/api/entregas/" + entrega.getId() + "/pronto")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.prontoParaDespachoEm", notNullValue()));
        }

        @Test
        @DisplayName("Entrega ja despachada: erro de transicao")
        void deveRecusarJaDespachada() throws Exception {
            Entrega entrega = cenario.entrega(loja, StatusEntrega.DESPACHADA);

            mockMvc.perform(post("/api/entregas/" + entrega.getId() + "/pronto")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("TRANSICAO_ESTADO_INVALIDA")));
        }
    }

    @Nested
    @DisplayName("POST /api/entregas/{id}/cancelar")
    class Cancelar {

        @Test
        @DisplayName("Sem motivo: 400 DADOS_INVALIDOS")
        void deveExigirMotivo() throws Exception {
            Entrega entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);

            mockMvc.perform(post("/api/entregas/" + entrega.getId() + "/cancelar")
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
            mockMvc.perform(get("/api/entregas"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo", is("NAO_AUTENTICADO")));
        }

        @Test
        @DisplayName("ADMIN_GERAL: 403 porque nao tem estabelecimento")
        void deveNegar403ParaAdminGeral() throws Exception {
            Usuario admin = cenario.usuario(null, PerfilUsuario.ADMIN_GERAL);

            mockMvc.perform(get("/api/entregas")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(admin)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN_RESTAURANTE: 200 (D004)")
        void devePermitirAdminRestaurante() throws Exception {
            Usuario admin = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE);
            cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);

            mockMvc.perform(get("/api/entregas")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(admin)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Isolamento de tenant: operador de A nao ve entregas de B")
        void deveIsolarTenants() throws Exception {
            Estabelecimento lojaB = cenario.estabelecimento();
            cenario.entrega(lojaB, StatusEntrega.AGUARDANDO_DESPACHO);

            mockMvc.perform(get("/api/entregas")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("ENTREGADOR: 403 ACESSO_NEGADO")
        void deveNegar403ParaEntregador() throws Exception {
            var motoboy = cenario.motoboy(loja);

            mockMvc.perform(get("/api/entregas")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(motoboy.getUsuario())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/entregas/{id}/ajustes")
    class AjustarRepasse {

        @Test
        @DisplayName("Deve ajustar repasse de entrega sem recibo")
        void deveAjustarRepasse() throws Exception {
            Entrega entrega = cenario.entrega(loja, StatusEntrega.ENTREGUE);
            cenario.caixa(loja);

            mockMvc.perform(post("/api/entregas/" + entrega.getId() + "/ajustes")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"valorNovo": 12.50, "motivo": "Correcao de faixa"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.valorNovo").value(12.50))
                    .andExpect(jsonPath("$.motivo", is("Correcao de faixa")));
        }

        @Test
        @DisplayName("Entrega com recibo: 422 AJUSTE_VALOR_NAO_PERMITIDO (Constituicao 5.1)")
        void deveRecusarAjusteComRecibo() throws Exception {
            Caixa caixaAberto = cenario.caixa(loja);
            Motoboy motoboy = cenario.motoboy(loja);
            Recibo recibo = cenario.recibo(loja, motoboy, caixaAberto, operador);
            Entrega entrega = cenario.entregaComRecibo(loja, recibo);

            mockMvc.perform(post("/api/entregas/" + entrega.getId() + "/ajustes")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"valorNovo": 15.00, "motivo": "Teste"}
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo", is("AJUSTE_VALOR_NAO_PERMITIDO")));
        }

        @Test
        @DisplayName("Sem motivo: 400 DADOS_INVALIDOS")
        void deveExigirMotivo() throws Exception {
            Entrega entrega = cenario.entrega(loja, StatusEntrega.ENTREGUE);

            mockMvc.perform(post("/api/entregas/" + entrega.getId() + "/ajustes")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"valorNovo": 12.50, "motivo": ""}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("DADOS_INVALIDOS")));
        }
    }

    @Nested
    @DisplayName("GET /api/entregas/{id}/ajustes")
    class ListarAjustes {

        @Test
        @DisplayName("Deve listar historico de ajustes vazio")
        void deveListarHistoricoVazio() throws Exception {
            Entrega entrega = cenario.entrega(loja, StatusEntrega.ENTREGUE);

            mockMvc.perform(get("/api/entregas/" + entrega.getId() + "/ajustes")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }
}
