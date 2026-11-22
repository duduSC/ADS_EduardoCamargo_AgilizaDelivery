package br.com.agilizadelivery.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.RollbackException;
import jakarta.validation.ConstraintViolationException;

/** Falhas de infraestrutura e do banco no status e codigo certos (T014, A). */
@ExtendWith(OutputCaptureExtension.class)
class TraducaoDeFalhasTest {

    private static final String DADO_PESSOAL = "maria.silva@cliente.example";

    @RestController
    static class ControladorQueFalha {

        private final RuntimeException falha;

        ControladorQueFalha(RuntimeException falha) {
            this.falha = falha;
        }

        @GetMapping("/falha")
        String falhar() {
            throw falha;
        }
    }

    static Stream<Arguments> falhas() {
        return Stream.of(
                caso("pool sem conexao", new CannotCreateTransactionException("x",
                        new SQLTransientConnectionException("timeout")), 503, "ERRO_INTERNO_SERVIDOR"),
                caso("conexao recusada", new DataAccessResourceFailureException("recusada"),
                        503, "ERRO_INTERNO_SERVIDOR"),
                caso("consulta expirou", new QueryTimeoutException("expirou"), 503, "ERRO_INTERNO_SERVIDOR"),
                caso("transacao expirou", new TransactionTimedOutException("expirou"), 503, "ERRO_INTERNO_SERVIDOR"),
                caso("conexao caiu no commit", new TransactionSystemException("commit",
                        new SQLException("caiu", "08006")), 503, "ERRO_INTERNO_SERVIDOR"),
                caso("H2 fechado", new IllegalStateException(new SQLException("closed", "90098", 90098)),
                        503, "ERRO_INTERNO_SERVIDOR"),

                caso("versao otimista", new OptimisticLockingFailureException("versao"), 409, "DADO_DUPLICADO"),
                caso("trava", new PessimisticLockingFailureException("trava"), 409, "DADO_DUPLICADO"),
                caso("espera da trava", new CannotAcquireLockException("espera"), 409, "DADO_DUPLICADO"),

                caso("e-mail", unicidade("uk_usuario_login_email"), 409, "EMAIL_JA_CADASTRADO"),
                caso("CNPJ", unicidade("uk_estabelecimento_cnpj"), 409, "CNPJ_JA_CADASTRADO"),
                caso("CPF", unicidade("uk_motoboy_cpf_hash"), 409, "CPF_JA_CADASTRADO"),
                caso("caixa", unicidade("uk_caixa_estabelecimento_data"), 409, "CAIXA_JA_ABERTO"),
                caso("pedido", unicidade("uk_pedido_externo_provedor_id_externo"), 409, "PEDIDO_JA_IMPORTADO"),
                caso("nome do H2", unicidade("\"PUBLIC.UK_USUARIO_LOGIN_EMAIL_INDEX_2 ON PUBLIC.USUARIO\""),
                        409, "EMAIL_JA_CADASTRADO"),
                caso("outra unicidade", unicidade("uk_qualquer"), 409, "DADO_DUPLICADO"),
                caso("chave estrangeira", integridade("23503", "fk_entrega_lote"), 409, "DADO_DUPLICADO"),
                caso("texto longo", integridade("22001", null), 400, "DADOS_INVALIDOS"),
                caso("numero grande", integridade("22003", null), 400, "DADOS_INVALIDOS"),
                caso("check no PostgreSQL", integridade("23514", "ck_lote_entrega_qtd_entregas"),
                        400, "DADOS_INVALIDOS"),
                caso("check no H2", integridade("23513", "ck_qualquer"), 400, "DADOS_INVALIDOS"),
                caso("nulo", integridade("23502", null), 400, "DADOS_INVALIDOS"),
                caso("validacao no commit", new TransactionSystemException("commit", new RollbackException(
                        new ConstraintViolationException("invalido", Set.of()))), 400, "DADOS_INVALIDOS"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("falhas")
    @DisplayName("Falha conhecida nunca vira 500")
    void falhaConhecida(String nome, RuntimeException falha, int status, String codigo, CapturedOutput saida)
            throws Exception {
        servidorQueFalhaCom(falha).perform(get("/falha"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.status", is(status)))
                .andExpect(jsonPath("$.codigo", is(codigo)))
                .andExpect(content().string(not(containsString(DADO_PESSOAL))))
                .andExpect(content().string(not(containsString("SQL"))));

        if (status < 500) {
            assertThat(saida.getAll())
                    .as("4xx: sem stacktrace e sem o valor rejeitado")
                    .doesNotContain("\tat ")
                    .doesNotContain(DADO_PESSOAL);
        }
    }

    @Test
    @DisplayName("Erro inesperado: 500 sem detalhe e um único stacktrace no servidor")
    void inesperado(CapturedOutput saida) throws Exception {
        servidorQueFalhaCom(new IllegalArgumentException("detalhe-interno-sigiloso"))
                .perform(get("/falha"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo", is("ERRO_INTERNO_SERVIDOR")))
                .andExpect(content().string(not(containsString("detalhe-interno-sigiloso"))));

        assertThat(saida.getAll().split("java.lang.IllegalArgumentException", -1)).hasSize(2);
    }

    private static MockMvc servidorQueFalhaCom(RuntimeException falha) {
        return MockMvcBuilders.standaloneSetup(new ControladorQueFalha(falha))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static Arguments caso(String nome, RuntimeException falha, int status, String codigo) {
        return Arguments.of(nome, falha, status, codigo);
    }

    private static DataIntegrityViolationException unicidade(String constraint) {
        return integridade("23505", constraint);
    }

    private static DataIntegrityViolationException integridade(String sqlState, String constraint) {
        String mensagem = "violacao; valor (" + DADO_PESSOAL + ")";
        return new DataIntegrityViolationException(mensagem, new org.hibernate.exception.ConstraintViolationException(
                mensagem, new SQLException(mensagem, sqlState), constraint));
    }
}
