package br.com.agilizadelivery.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;

import javax.sql.DataSource;

import org.h2.engine.SessionLocal;
import org.h2.jdbc.JdbcConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.zaxxer.hikari.HikariDataSource;

import br.com.agilizadelivery.controller.CenarioWeb;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;

/**
 * Incidente de 17/11: "The database has been closed" (H2, 90098).
 *
 * Sem memoria, o H2 executa um desligamento de emergencia
 * (Database.shutdownImmediately). As conexoes do pool passam a falhar, mas
 * continuam respondendo isValid() = true; as novas abrem um banco vazio,
 * tambem "valido". O teste provoca esse mesmo desligamento.
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:indisponivel;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class BancoIndisponivelTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioWeb cenario;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Banco fechado: 503 no formato padrão e health DOWN, em vez de 500 com health UP")
    void bancoFechado(CapturedOutput saida) throws Exception {
        Usuario operador = cenario.usuario(cenario.estabelecimento(), PerfilUsuario.OPERADOR);
        String bearer = cenario.bearer(operador);
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        login(operador).andExpect(status().isOk());

        try (Connection conexao = dataSource.getConnection()) {
            ((SessionLocal) conexao.unwrap(JdbcConnection.class).getSession()).getDatabase().shutdownImmediately();
        }

        exigirIndisponivel(operador, bearer);

        // as conexoes seguintes do pool caem num banco novo e vazio
        dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().softEvictConnections();
        exigirIndisponivel(operador, bearer);

        assertThat(saida.getAll()).doesNotContain(CenarioWeb.SENHA);
    }

    private void exigirIndisponivel(Usuario operador, String bearer) throws Exception {
        login(operador)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo", is("ERRO_INTERNO_SERVIDOR")))
                .andExpect(content().string(not(containsString("90098"))))
                .andExpect(content().string(not(containsString("Exception"))));
        mockMvc.perform(get("/api/entregas").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo", is("ERRO_INTERNO_SERVIDOR")));
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is("DOWN")))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    private ResultActions login(Usuario usuario) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginEmail\":\"%s\",\"senha\":\"%s\"}"
                        .formatted(usuario.getLoginEmail(), CenarioWeb.SENHA)));
    }
}
