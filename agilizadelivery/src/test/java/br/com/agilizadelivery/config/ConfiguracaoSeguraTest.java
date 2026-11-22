package br.com.agilizadelivery.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import br.com.agilizadelivery.seguranca.jwt.JwtProperties;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;

/**
 * Padroes de configuracao (T014, C): no postgres a carga e o worker nunca
 * ligam sozinhos, o console do H2 fica desligado e o segredo do JWT tem
 * tamanho minimo.
 */
class ConfiguracaoSeguraTest {

    /** O ambiente da maquina fica de fora: interessa o padrao dos YAML. */
    private static StandardEnvironment perfil(String nome) {
        StandardEnvironment ambiente = new StandardEnvironment();
        ambiente.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        ambiente.getPropertySources().addFirst(new MapPropertySource("teste", Map.of(
                "spring.profiles.active", nome,
                "spring.config.location", "file:src/main/resources/")));
        ConfigDataEnvironmentPostProcessor.applyTo(ambiente);
        return ambiente;
    }

    @Test
    @DisplayName("Perfil postgres: carga e worker desligados e esquema só validado")
    void postgres() {
        StandardEnvironment ambiente = perfil("postgres");

        assertThat(ambiente.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(ambiente.getProperty("agiliza.demo.carga-inicial")).isEqualTo("false");
        assertThat(ambiente.getProperty("agiliza.integracao.worker.habilitado")).isEqualTo("false");
        assertThat(ambiente.getProperty("spring.h2.console.enabled", "false")).isEqualTo("false");
    }

    @ParameterizedTest
    @ValueSource(strings = { "h2", "postgres" })
    @DisplayName("Todo perfil: console do H2 desligado, só health e sem detalhe de erro")
    void todosOsPerfis(String nome) {
        StandardEnvironment ambiente = perfil(nome);

        assertThat(ambiente.getProperty("spring.h2.console.enabled", "false")).isEqualTo("false");
        assertThat(ambiente.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(ambiente.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(ambiente.getProperty("spring.web.error.include-stacktrace")).isEqualTo("never");
        assertThat(ambiente.getProperty("spring.mvc.log-resolved-exception")).isEqualTo("false");
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 16, 31 })
    @DisplayName("Segredo do JWT com menos de 32 bytes não sobe")
    void segredoCurto(int bytes) {
        String segredo = Base64.getEncoder().encodeToString(new byte[bytes]);
        JwtProperties propriedades = new JwtProperties(segredo, Duration.ofHours(3), Duration.ofDays(30), "teste");

        assertThatThrownBy(() -> new ServicoJwt(propriedades))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agiliza.jwt.segredo");
    }
}
