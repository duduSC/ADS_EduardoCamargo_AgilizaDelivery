package br.com.agilizadelivery.exception.handler;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

/**
 * Le a cadeia de causas de uma falha de persistencia. O tipo de fora varia
 * com o ponto em que ela acontece (consulta, flush ou commit), entao quem
 * decide e o SQLState, o codigo do banco ou o tipo da causa.
 */
final class FalhaDeBanco {

    /** PostgreSQL desligando ou reiniciando, sem conexoes livres, consulta cancelada por timeout. */
    private static final Set<String> ESTADOS_INDISPONIVEL = Set.of("57P01", "57P02", "57P03", "53300", "57014");

    /**
     * H2: 90098 banco fechado pelo desligamento de emergencia, 90108 sem
     * memoria, 90121 ja fechado, 90067 conexao caida, 42104 banco vazio (o
     * que uma conexao nova abre depois do desligamento).
     */
    private static final Set<Integer> H2_INDISPONIVEL = Set.of(90098, 90108, 90121, 90067, 42104);

    private static final Set<String> ESTADOS_CONCORRENCIA = Set.of("40001", "40P01", "55P03", "HYT00");

    /** Nao nulo, check do PostgreSQL e check do H2. */
    private static final Set<String> ESTADOS_DADO_INVALIDO = Set.of("23502", "23514", "23513");

    /** Nomes da V1 do Flyway, repetidos nas entidades para valerem tambem no H2. */
    private static final Map<String, CodigoErro> CODIGO_POR_CONSTRAINT = Map.of(
            "uk_usuario_login_email", CodigoErro.EMAIL_JA_CADASTRADO,
            "uk_estabelecimento_cnpj", CodigoErro.CNPJ_JA_CADASTRADO,
            "uk_motoboy_cpf_hash", CodigoErro.CPF_JA_CADASTRADO,
            "uk_caixa_estabelecimento_data", CodigoErro.CAIXA_JA_ABERTO,
            "uk_pedido_externo_provedor_id_externo", CodigoErro.PEDIDO_JA_IMPORTADO);

    private FalhaDeBanco() {
    }

    static boolean indisponivel(Throwable falha) {
        return causas(falha).anyMatch(causa -> causa instanceof DataAccessResourceFailureException
                || causa instanceof CannotCreateTransactionException
                || causa instanceof QueryTimeoutException
                || causa instanceof TransactionTimedOutException
                || causa instanceof TransientDataAccessResourceException
                || causa instanceof RecoverableDataAccessException
                || causa instanceof JDBCConnectionException
                || causa instanceof SQLTransientConnectionException
                || causa instanceof SQLNonTransientConnectionException
                || causa instanceof SQLException sql && sqlIndisponivel(sql));
    }

    static boolean concorrencia(Throwable falha) {
        return causas(falha).anyMatch(causa -> causa instanceof ConcurrencyFailureException
                || causa instanceof jakarta.persistence.OptimisticLockException
                || causa instanceof jakarta.persistence.PessimisticLockException
                || causa instanceof jakarta.persistence.LockTimeoutException
                || causa instanceof SQLException sql && sql.getSQLState() != null
                        && ESTADOS_CONCORRENCIA.contains(sql.getSQLState()));
    }

    /** Valor que a coluna nao aceita (tamanho, faixa, nulo, check), e nao um conflito. */
    static boolean dadoInvalido(Throwable falha) {
        return sqlState(falha)
                .filter(estado -> estado.startsWith("22") || ESTADOS_DADO_INVALIDO.contains(estado))
                .isPresent();
    }

    static Optional<CodigoErro> codigoDaConstraint(Throwable falha) {
        // So o nome: a mensagem traz os valores da linha.
        List<String> nomes = causas(falha)
                .filter(org.hibernate.exception.ConstraintViolationException.class::isInstance)
                .map(causa -> ((org.hibernate.exception.ConstraintViolationException) causa).getConstraintName())
                .filter(Objects::nonNull)
                .map(nome -> nome.toLowerCase(Locale.ROOT))
                .toList();
        return CODIGO_POR_CONSTRAINT.entrySet().stream()
                .filter(par -> nomes.stream().anyMatch(nome -> nome.contains(par.getKey())))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    static Optional<String> sqlState(Throwable falha) {
        return causas(falha)
                .filter(SQLException.class::isInstance)
                .map(causa -> Objects.requireNonNullElse(((SQLException) causa).getSQLState(), ""))
                .filter(estado -> !estado.isEmpty())
                .findFirst();
    }

    static <T extends Throwable> Optional<T> causa(Throwable falha, Class<T> tipo) {
        return causas(falha).filter(tipo::isInstance).map(tipo::cast).findFirst();
    }

    private static boolean sqlIndisponivel(SQLException sql) {
        String estado = Objects.requireNonNullElse(sql.getSQLState(), "");
        return estado.startsWith("08")
                || ESTADOS_INDISPONIVEL.contains(estado)
                || H2_INDISPONIVEL.contains(sql.getErrorCode());
    }

    private static Stream<Throwable> causas(Throwable falha) {
        return Stream.iterate(falha, Objects::nonNull,
                atual -> atual.getCause() == atual ? null : atual.getCause()).limit(25);
    }
}
