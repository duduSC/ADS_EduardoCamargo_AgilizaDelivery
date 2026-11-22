package br.com.agilizadelivery.config;

import java.math.BigDecimal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.deser.jdk.NumberDeserializers;
import tools.jackson.databind.module.SimpleModule;

/**
 * Recusa decimal com expoente absurdo na leitura do JSON (T014).
 *
 * 1e-999999999 tem poucos bytes, passa na faixa da latitude e, ao virar
 * texto (chave do cache de rotas) ou coluna, ocupa um gigabyte: um unico
 * request esgota a memoria, e sem memoria o H2 se desliga para todas as
 * lojas (incidente de 17/11). O limite do Jackson e de caracteres lidos,
 * e nao cobre o expoente.
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfig {

    /** Folga larga: um double por extenso tem por volta de 20 casas. */
    static final int ESCALA_MAXIMA = 100;

    @Bean
    JacksonModule decimaisLimitados() {
        return new SimpleModule("decimais-limitados").addDeserializer(BigDecimal.class, new DecimalLimitado());
    }

    static final class DecimalLimitado extends NumberDeserializers.BigDecimalDeserializer {

        @Override
        public BigDecimal deserialize(JsonParser parser, DeserializationContext contexto) {
            BigDecimal valor = super.deserialize(parser, contexto);
            if (valor != null && Math.abs((long) valor.scale()) > ESCALA_MAXIMA) {
                return contexto.reportInputMismatch(BigDecimal.class, "Número com expoente fora do limite.");
            }
            return valor;
        }
    }
}
