package br.com.agilizadelivery.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.FalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.EtapaFalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusFalhaIntegracao;
import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import br.com.agilizadelivery.repository.FalhaIntegracaoRepository;
import br.com.agilizadelivery.tempo.HorarioLocal;

/** Endpoints que o inventario da T014 achou sem teste de controller. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AcoesSemCoberturaTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private ConfiguracaoIntegracaoRepository configuracaoRepository;
    @Autowired
    private FalhaIntegracaoRepository falhaRepository;

    private Estabelecimento loja;
    private String admin;
    private String operador;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
        admin = cenario.bearer(cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE));
        operador = cenario.bearer(cenario.usuario(loja, PerfilUsuario.OPERADOR));
    }

    private Long falhaPendente() {
        ConfiguracaoIntegracao configuracao = new ConfiguracaoIntegracao();
        configuracao.setEstabelecimento(loja);
        configuracao.setProvedor("SIMULADOR");
        configuracao.setMerchantId("acoes-" + loja.getId());
        configuracao.setAtivo(true);
        configuracaoRepository.save(configuracao);
        FalhaIntegracao falha = new FalhaIntegracao();
        falha.setConfiguracaoIntegracao(configuracao);
        falha.setEtapa(EtapaFalhaIntegracao.PERSISTENCIA);
        falha.setMensagemErro("falha");
        falha.setTentativas((short) 5);
        falha.setStatus(StatusFalhaIntegracao.PENDENTE);
        falha.setOcorridoEm(HorarioLocal.agora());
        return falhaRepository.save(falha).getId();
    }

    @ParameterizedTest
    @CsvSource({ "reprocessar,REPROCESSADA", "descartar,DESCARTADA" })
    @DisplayName("Admin resolve a falha uma vez; operador não resolve")
    void resolverFalha(String acao, String resultado) throws Exception {
        String caminho = "/api/falhas-integracao/" + falhaPendente() + "/" + acao;

        mockMvc.perform(post(caminho).header(HttpHeaders.AUTHORIZATION, operador))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
        mockMvc.perform(post(caminho).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(resultado)))
                .andExpect(jsonPath("$.resolvidoEm", notNullValue()));
        mockMvc.perform(post(caminho).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("REGRA_NEGOCIO_VIOLADA")));
    }

    @Test
    @DisplayName("PATCH /api/entregas/{id}/coordenada recalcula; entrega final é recusada")
    void ajustarPino() throws Exception {
        Entrega pendente = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
        Entrega finalizada = cenario.entrega(loja, StatusEntrega.ENTREGUE);
        String corpo = "{\"latitude\":-23.5700000,\"longitude\":-46.6600000}";

        mockMvc.perform(patch("/api/entregas/" + pendente.getId() + "/coordenada")
                        .header(HttpHeaders.AUTHORIZATION, operador)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coordenada.latitude", is(-23.57)))
                .andExpect(jsonPath("$.distanciaKm", notNullValue()));
        mockMvc.perform(patch("/api/entregas/" + finalizada.getId() + "/coordenada")
                        .header(HttpHeaders.AUTHORIZATION, operador)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo", is("REGRA_NEGOCIO_VIOLADA")));
    }
}
