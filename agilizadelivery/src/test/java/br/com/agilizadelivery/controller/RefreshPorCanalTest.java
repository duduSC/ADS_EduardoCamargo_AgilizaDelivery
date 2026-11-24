package br.com.agilizadelivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;
import br.com.agilizadelivery.service.CanalAcesso;

/** O refresh fica no canal em que nasceu e reconfere o cadastro (UC07 E2 a E4). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefreshPorCanalTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private ServicoJwt servicoJwt;

    private Estabelecimento loja;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
    }

    @ParameterizedTest
    @CsvSource({ "ENTREGADOR,APP", "OPERADOR,BACKOFFICE", "ADMIN_RESTAURANTE,BACKOFFICE" })
    @DisplayName("Renovação devolve tokens do mesmo canal")
    void mesmoCanal(PerfilUsuario perfil, CanalAcesso canal) throws Exception {
        Usuario usuario = perfil == PerfilUsuario.ENTREGADOR
                ? cenario.motoboy(loja).getUsuario() : cenario.usuario(loja, perfil);

        String corpo = renovar(usuario, canal).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(servicoJwt.validarTokenAcesso(JsonPath.read(corpo, "$.token")).canal()).isEqualTo(canal);
        assertThat(servicoJwt.validarRefreshToken(JsonPath.read(corpo, "$.refreshToken")).canal()).isEqualTo(canal);
    }

    @ParameterizedTest
    @CsvSource({ "ENTREGADOR,BACKOFFICE", "OPERADOR,APP" })
    @DisplayName("Refresh de outro canal é recusado")
    void outroCanal(PerfilUsuario perfil, CanalAcesso canal) throws Exception {
        Usuario usuario = perfil == PerfilUsuario.ENTREGADOR
                ? cenario.motoboy(loja).getUsuario() : cenario.usuario(loja, perfil);

        renovar(usuario, canal).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo", is("ACESSO_NEGADO")));
    }

    @Test
    @DisplayName("Entregador bloqueado depois do login não renova")
    void bloqueado() throws Exception {
        Motoboy motoboy = cenario.motoboy(loja, StatusCadastroMotoboy.BLOQUEADO, StatusDisponibilidadeMotoboy.OFFLINE);

        renovar(motoboy.getUsuario(), CanalAcesso.APP)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo", is("USUARIO_BLOQUEADO")));
    }

    @Test
    @DisplayName("Loja inativada depois do login não renova")
    void lojaInativa() throws Exception {
        Usuario operador = cenario.usuario(
                cenario.estabelecimento(StatusEstabelecimento.INATIVO, (short) 5), PerfilUsuario.OPERADOR);

        renovar(operador, CanalAcesso.BACKOFFICE)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo", is("ESTABELECIMENTO_INATIVO")));
    }

    private ResultActions renovar(Usuario usuario, CanalAcesso canal) throws Exception {
        String refresh = servicoJwt.gerarRefreshToken(usuario.getId(), canal);
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"));
    }
}
