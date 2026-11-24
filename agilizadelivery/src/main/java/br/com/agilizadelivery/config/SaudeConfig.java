package br.com.agilizadelivery.config;

import javax.sql.DataSource;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * O health precisa consultar uma tabela: depois do desligamento de
 * emergencia do H2 (incidente de 17/11), a checagem padrao, isValid(),
 * continua dizendo UP, tanto com o banco fechado quanto com o banco vazio
 * que uma conexao nova abre. Os detalhes nunca saem (show-details: never).
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class SaudeConfig {

    private static final String CONSULTA = "select id from estabelecimento where 1 = 0";

    @Bean
    HealthIndicator esquemaHealthIndicator(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return () -> {
            try {
                jdbc.queryForList(CONSULTA);
                return Health.up().build();
            } catch (DataAccessException e) {
                log.warn("Health DOWN, banco ou esquema indisponível: {}", e.getClass().getSimpleName());
                return Health.down().build();
            }
        };
    }
}
