package br.com.agilizadelivery.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.controller.CenarioWeb;
import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.dto.request.AdministradorInicialRequest;
import br.com.agilizadelivery.dto.request.CriarEstabelecimentoRequest;
import br.com.agilizadelivery.dto.request.CriarMotoboyRequest;
import br.com.agilizadelivery.dto.request.CriarUsuarioRequest;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.repository.UsuarioRepository;
import jakarta.servlet.RequestDispatcher;

/**
 * Nada de stacktrace na resposta nem de senha, CPF, telefone ou valor
 * rejeitado no log (Constituicao 6.1 a 6.4), mesmo com os padroes que o
 * devtools liga no container de desenvolvimento.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = SemVazamentoTest.PadroesDoDevtools.class)
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class SemVazamentoTest {

    /** defaults.* de META-INF/spring-devtools.properties do Boot 4.1, na menor precedencia. */
    static class PadroesDoDevtools implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext contexto) {
            contexto.getEnvironment().getPropertySources().addLast(new MapPropertySource("padroesDevtools", Map.of(
                    "spring.web.error.include-binding-errors", "always",
                    "spring.web.error.include-message", "always",
                    "spring.web.error.include-stacktrace", "always",
                    "spring.mvc.log-resolved-exception", "true")));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioWeb cenario;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Página de erro não devolve mensagem nem stacktrace da exceção")
    void erroSemStacktrace() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/entregas")
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "detalhe-interno")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("detalhe-interno")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo", is("ERRO_INTERNO_SERVIDOR")))
                .andExpect(jsonPath("$.path", is("/api/entregas")))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("detalhe-interno"))));
    }

    @Test
    @DisplayName("Validação recusada não põe CPF, senha nem telefone no log")
    void validacaoSemDadoSensivel(CapturedOutput saida) throws Exception {
        Usuario admin = cenario.usuario(cenario.estabelecimento(), PerfilUsuario.ADMIN_RESTAURANTE);

        mockMvc.perform(post("/api/motoboys")
                        .header(HttpHeaders.AUTHORIZATION, cenario.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Joao","loginEmail":"joao@teste.com","senha":"s3nh@9",
                                 "cpf":"12345678900","telefone":"5499887766554433"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(saida.getAll()).doesNotContain("12345678900", "s3nh@9", "5499887766554433");
    }

    @Test
    @DisplayName("Violação de unicidade não põe os valores da linha no log")
    void integridadeSemValores(CapturedOutput saida) {
        Usuario existente = cenario.usuario(cenario.estabelecimento(), PerfilUsuario.OPERADOR);
        Usuario novo = new Usuario();
        novo.setEstabelecimento(existente.getEstabelecimento());
        novo.setNome("Outro");
        novo.setLoginEmail(existente.getLoginEmail());
        novo.setSenhaHash(existente.getSenhaHash());
        novo.setPerfil(PerfilUsuario.OPERADOR);

        try {
            usuarioRepository.saveAndFlush(novo);
        } catch (RuntimeException esperada) {
            assertThat(esperada).isNotNull();
        }

        assertThat(saida.getAll()).doesNotContain(existente.getLoginEmail(), existente.getSenhaHash());
    }

    @Test
    @DisplayName("toString dos requests mascara senha, CPF e telefone (o Spring os imprime em DEBUG)")
    void requestsMascarados() {
        assertThat(new CriarMotoboyRequest("Joao", "j@x.com", "senha-secreta", "52998224725", "54999998888", null)
                .toString())
                .doesNotContain("senha-secreta", "52998224725", "54999998888");
        assertThat(new CriarUsuarioRequest("Ana", "a@x.com", "senha-secreta", PerfilUsuario.OPERADOR).toString())
                .doesNotContain("senha-secreta");
        assertThat(new CriarEstabelecimentoRequest("Loja Ltda", "Loja", "11222333000181", "54999998888", "Rua A",
                new CoordenadaDto(BigDecimal.ONE, BigDecimal.ONE), (short) 5, null,
                new AdministradorInicialRequest("Ana", "a@x.com", "senha-secreta")).toString())
                .doesNotContain("senha-secreta", "54999998888");
    }
}
