package br.com.agilizadelivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;
import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.ReciboRepository;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;
import br.com.agilizadelivery.service.CanalAcesso;
import jakarta.persistence.EntityManager;

/**
 * Recibos do proprio entregador no aplicativo (D013 item 2; T015 item 1).
 *
 * O entregador vem do token: recibo de outro entregador, da mesma loja ou
 * de outra, responde 404, igual a um id que nao existe. Os horarios de
 * emissao (criado_em) sao gravados direto no banco, para a ordem da lista
 * nao depender da resolucao do relogio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EntregadorRecibosControllerTest {

    private static final LocalDate ONTEM = LocalDate.of(2026, 11, 19);
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 20);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private ReciboRepository reciboRepository;
    @Autowired
    private EntregaRepository entregaRepository;
    @Autowired
    private ServicoJwt servicoJwt;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private Estabelecimento loja;
    private Usuario operador;
    private Motoboy motoboy;
    private Caixa caixaOntem;
    private Recibo pago;
    private Recibo gerado;
    private Recibo cancelado;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        motoboy = cenario.motoboy(loja);
        caixaOntem = cenario.caixa(loja, ONTEM, StatusCaixa.FECHADO);
        Caixa caixaHoje = cenario.caixa(loja, HOJE, StatusCaixa.ABERTO);

        pago = cenario.recibo(loja, motoboy, caixaOntem, operador);
        pago.setStatus(StatusRecibo.PAGO);
        pago.setPagoEm(ONTEM.atTime(22, 30));
        pago.setQtdEntregas((short) 2);
        pago.setValorTotal(new BigDecimal("18.00"));
        reciboRepository.save(pago);
        entregaFinalizada(pago, "Segunda", ONTEM.atTime(20, 0));
        entregaFinalizada(pago, "Primeira", ONTEM.atTime(19, 0));

        gerado = cenario.recibo(loja, motoboy, caixaHoje, operador);
        entregaFinalizada(gerado, "Hoje", HOJE.atTime(12, 0));

        // Cancelado depois de pago ou gerado: as entregas foram desvinculadas (UC03 A2)
        cancelado = cenario.recibo(loja, motoboy, caixaOntem, operador);
        cancelado.setStatus(StatusRecibo.CANCELADO);
        cancelado.setQtdEntregas((short) 3);
        cancelado.setValorTotal(new BigDecimal("27.00"));
        reciboRepository.save(cancelado);

        emitidoEm(pago, ONTEM.atTime(22, 10));
        emitidoEm(cancelado, ONTEM.atTime(23, 0));
        emitidoEm(gerado, HOJE.atTime(12, 30));
    }

    @Nested
    @DisplayName("Lista")
    class Lista {

        @Test
        @DisplayName("Os proprios recibos nos tres status: dia do caixa mais recente primeiro, depois a emissao")
        void lista() throws Exception {
            recibos(motoboy.getUsuario())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].id", is(gerado.getId().toString())))
                    .andExpect(jsonPath("$[0].status", is("GERADO")))
                    .andExpect(jsonPath("$[0].dataReferencia", is("2026-11-20")))
                    .andExpect(jsonPath("$[0].periodoInicio", is("2026-11-20T00:00:00")))
                    .andExpect(jsonPath("$[0].periodoFim", is("2026-11-20T23:59:00")))
                    .andExpect(jsonPath("$[0].qtdEntregas", is(1)))
                    .andExpect(jsonPath("$[0].valorTotal", is(9.00)))
                    .andExpect(jsonPath("$[0].pagoEm", nullValue()))
                    .andExpect(jsonPath("$[0].criadoEm", is("2026-11-20T12:30:00")))
                    .andExpect(jsonPath("$[1].id", is(cancelado.getId().toString())))
                    .andExpect(jsonPath("$[1].status", is("CANCELADO")))
                    .andExpect(jsonPath("$[1].criadoEm", is("2026-11-19T23:00:00")))
                    .andExpect(jsonPath("$[2].id", is(pago.getId().toString())))
                    .andExpect(jsonPath("$[2].status", is("PAGO")))
                    .andExpect(jsonPath("$[2].qtdEntregas", is(2)))
                    .andExpect(jsonPath("$[2].valorTotal", is(18.00)))
                    .andExpect(jsonPath("$[2].pagoEm", is("2026-11-19T22:30:00")))
                    .andExpect(jsonPath("$[2].criadoEm", is("2026-11-19T22:10:00")));
        }

        @Test
        @DisplayName("Sem as entregas e sem os ids internos do caixa, do operador e do entregador")
        void semCamposInternos() throws Exception {
            recibos(motoboy.getUsuario())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].entregas").doesNotExist())
                    .andExpect(jsonPath("$[0].caixaId").doesNotExist())
                    .andExpect(jsonPath("$[0].usuarioGeradorId").doesNotExist())
                    .andExpect(jsonPath("$[0].motoboyId").doesNotExist())
                    .andExpect(jsonPath("$[0].estabelecimentoId").doesNotExist());
        }

        @Test
        @DisplayName("No mesmo dia de caixa, a emissao mais recente vem primeiro")
        void mesmoDiaPelaEmissao() throws Exception {
            Recibo refeito = cenario.recibo(loja, motoboy, caixaOntem, operador);
            emitidoEm(refeito, ONTEM.atTime(23, 30));

            recibos(motoboy.getUsuario())
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$[0].id", is(gerado.getId().toString())))
                    .andExpect(jsonPath("$[1].id", is(refeito.getId().toString())))
                    .andExpect(jsonPath("$[2].id", is(cancelado.getId().toString())))
                    .andExpect(jsonPath("$[3].id", is(pago.getId().toString())));
        }

        @Test
        @DisplayName("Mesmo dia e mesma emissao: a ordem nao varia entre consultas")
        void ordemEstavelNoEmpate() throws Exception {
            Recibo a = cenario.recibo(loja, motoboy, caixaOntem, operador);
            Recibo b = cenario.recibo(loja, motoboy, caixaOntem, operador);
            emitidoEm(a, ONTEM.atTime(23, 45));
            emitidoEm(b, ONTEM.atTime(23, 45));

            List<String> primeira = ids(recibos(motoboy.getUsuario()));
            List<String> segunda = ids(recibos(motoboy.getUsuario()));

            assertThat(primeira).hasSize(5).isEqualTo(segunda);
            assertThat(primeira.subList(1, 3))
                    .containsExactlyInAnyOrder(a.getId().toString(), b.getId().toString());
        }
    }

    @Nested
    @DisplayName("Detalhe")
    class Detalhe {

        @Test
        @DisplayName("PAGO: os campos da lista e as entregas na ordem de finalizacao")
        void pago() throws Exception {
            recibo(pago.getId(), motoboy.getUsuario())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(pago.getId().toString())))
                    .andExpect(jsonPath("$.status", is("PAGO")))
                    .andExpect(jsonPath("$.dataReferencia", is("2026-11-19")))
                    .andExpect(jsonPath("$.periodoInicio", is("2026-11-19T00:00:00")))
                    .andExpect(jsonPath("$.periodoFim", is("2026-11-19T23:59:00")))
                    .andExpect(jsonPath("$.qtdEntregas", is(2)))
                    .andExpect(jsonPath("$.valorTotal", is(18.00)))
                    .andExpect(jsonPath("$.pagoEm", is("2026-11-19T22:30:00")))
                    .andExpect(jsonPath("$.criadoEm", is("2026-11-19T22:10:00")))
                    .andExpect(jsonPath("$.entregas", hasSize(2)))
                    .andExpect(jsonPath("$.entregas[0].nomeCliente", is("Primeira")))
                    .andExpect(jsonPath("$.entregas[0].entregaId", notNullValue()))
                    .andExpect(jsonPath("$.entregas[0].finalizadaEm", is("2026-11-19T19:00:00")))
                    .andExpect(jsonPath("$.entregas[0].status", is("ENTREGUE")))
                    .andExpect(jsonPath("$.entregas[0].distanciaKm", is(2.10)))
                    .andExpect(jsonPath("$.entregas[0].valorRepasse", is(9.00)))
                    .andExpect(jsonPath("$.entregas[1].nomeCliente", is("Segunda")))
                    .andExpect(jsonPath("$.entregas[1].finalizadaEm", is("2026-11-19T20:00:00")));
        }

        @Test
        @DisplayName("GERADO: aguardando pagamento, sem pagoEm, com a entrega do dia")
        void gerado() throws Exception {
            recibo(gerado.getId(), motoboy.getUsuario())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("GERADO")))
                    .andExpect(jsonPath("$.pagoEm", nullValue()))
                    .andExpect(jsonPath("$.entregas", hasSize(1)))
                    .andExpect(jsonPath("$.entregas[0].nomeCliente", is("Hoje")))
                    .andExpect(jsonPath("$.entregas[0].finalizadaEm", is("2026-11-20T12:00:00")));
        }

        @Test
        @DisplayName("CANCELADO: sem entregas (o cancelamento as desvincula), com os totais da emissao")
        void cancelado() throws Exception {
            recibo(cancelado.getId(), motoboy.getUsuario())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("CANCELADO")))
                    .andExpect(jsonPath("$.qtdEntregas", is(3)))
                    .andExpect(jsonPath("$.valorTotal", is(27.00)))
                    .andExpect(jsonPath("$.entregas", hasSize(0)));
        }

        @Test
        @DisplayName("Entrega e status de cada linha: FALHA e CANCELADA durante o percurso tambem entram")
        void statusDasEntregas() throws Exception {
            Entrega falha = entregaFinalizada(gerado, "Falhou", HOJE.atTime(13, 0));
            falha.setStatus(StatusEntrega.FALHA);
            falha.setMotivoFalha("Cliente ausente");
            entregaRepository.save(falha);
            Entrega cancelada = entregaFinalizada(gerado, "Cancelou", HOJE.atTime(14, 0));
            cancelada.setStatus(StatusEntrega.CANCELADA);
            entregaRepository.save(cancelada);

            recibo(gerado.getId(), motoboy.getUsuario())
                    .andExpect(jsonPath("$.entregas", hasSize(3)))
                    .andExpect(jsonPath("$.entregas[0].status", is("ENTREGUE")))
                    .andExpect(jsonPath("$.entregas[1].status", is("FALHA")))
                    .andExpect(jsonPath("$.entregas[2].status", is("CANCELADA")));
        }

        @Test
        @DisplayName("Entrega de outra loja vinculada por erro ao recibo nao aparece (filtro por loja)")
        void entregaDeOutraLoja() throws Exception {
            Estabelecimento outraLoja = cenario.estabelecimento();
            Entrega intrusa = cenario.entrega(outraLoja, StatusEntrega.ENTREGUE);
            intrusa.setRecibo(gerado);
            intrusa.setNomeCliente("De outra loja");
            entregaRepository.save(intrusa);

            recibo(gerado.getId(), motoboy.getUsuario())
                    .andExpect(jsonPath("$.entregas", hasSize(1)))
                    .andExpect(jsonPath("$.entregas[0].nomeCliente", is("Hoje")));
        }
    }

    @Nested
    @DisplayName("Isolamento e acesso")
    class Isolamento {

        @Test
        @DisplayName("Outro entregador da mesma loja: lista vazia e 404 no detalhe, igual a um id inexistente")
        void entreEntregadores() throws Exception {
            Motoboy outro = cenario.motoboy(loja);
            recibos(outro.getUsuario())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
            recibo(pago.getId(), outro.getUsuario())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo", is("RECURSO_NAO_ENCONTRADO")))
                    .andExpect(jsonPath("$.message", is("Recibo não encontrado(a) com identificador: " + pago.getId())));
            UUID inexistente = UUID.randomUUID();
            recibo(inexistente, motoboy.getUsuario())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo", is("RECURSO_NAO_ENCONTRADO")))
                    .andExpect(jsonPath("$.message", is("Recibo não encontrado(a) com identificador: " + inexistente)));
        }

        @Test
        @DisplayName("Entregador de outra loja: 404 no detalhe e nada na lista")
        void entreLojas() throws Exception {
            Estabelecimento outraLoja = cenario.estabelecimento();
            Motoboy deOutraLoja = cenario.motoboy(outraLoja);
            recibo(gerado.getId(), deOutraLoja.getUsuario())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo", is("RECURSO_NAO_ENCONTRADO")));
            recibos(deOutraLoja.getUsuario())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Id malformado: 400 PARAMETRO_INVALIDO")
        void idMalformado() throws Exception {
            mockMvc.perform(get("/api/entregador/recibos/{id}", "nao-e-uuid")
                            .header(HttpHeaders.AUTHORIZATION, cenario.bearer(motoboy.getUsuario())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo", is("PARAMETRO_INVALIDO")));
        }

        @Test
        @DisplayName("Operador (BACKOFFICE) e entregador com token do BACKOFFICE: 403 na lista e no detalhe")
        void perfilECanal() throws Exception {
            recibos(operador)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
            recibo(pago.getId(), operador).andExpect(status().isForbidden());

            Usuario usuario = motoboy.getUsuario();
            String tokenDoPainel = "Bearer " + servicoJwt.gerarTokenAcesso(
                    new UsuarioAutenticadoResponse(usuario.getId(), loja.getId(), null, usuario.getNome(),
                            PerfilUsuario.ENTREGADOR),
                    CanalAcesso.BACKOFFICE);
            mockMvc.perform(get("/api/entregador/recibos").header(HttpHeaders.AUTHORIZATION, tokenDoPainel))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
            mockMvc.perform(get("/api/entregador/recibos/{id}", pago.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenDoPainel))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sem token: 401")
        void semToken() throws Exception {
            mockMvc.perform(get("/api/entregador/recibos")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/entregador/recibos/{id}", pago.getId())).andExpect(status().isUnauthorized());
        }
    }

    private ResultActions recibos(Usuario quem) throws Exception {
        return mockMvc.perform(get("/api/entregador/recibos")
                .header(HttpHeaders.AUTHORIZATION, cenario.bearer(quem)));
    }

    private ResultActions recibo(UUID id, Usuario quem) throws Exception {
        return mockMvc.perform(get("/api/entregador/recibos/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, cenario.bearer(quem)));
    }

    private static List<String> ids(ResultActions resposta) throws Exception {
        return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$[*].id");
    }

    private Entrega entregaFinalizada(Recibo recibo, String cliente, LocalDateTime finalizadaEm) {
        Entrega entrega = cenario.entregaComRecibo(loja, recibo);
        entrega.setNomeCliente(cliente);
        entrega.setFinalizadaEm(finalizadaEm);
        return entregaRepository.save(entrega);
    }

    /** criado_em nao e atualizavel pelo JPA: grava direto e descarta o contexto. */
    private void emitidoEm(Recibo recibo, LocalDateTime emissao) {
        entityManager.flush();
        jdbc.update("update recibo set criado_em = ? where id = ?", emissao, recibo.getId());
        entityManager.clear();
    }
}
