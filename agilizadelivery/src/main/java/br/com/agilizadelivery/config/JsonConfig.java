package br.com.agilizadelivery.config;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.agilizadelivery.tempo.HorarioLocal;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.deser.jdk.NumberDeserializers;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.module.SimpleModule;

/** Endurece a leitura do JSON de entrada; a saida nao muda (T014). */
@Configuration(proxyBeanMethods = false)
public class JsonConfig {

    /** Folga larga: um double por extenso tem por volta de 20 casas. */
    static final int ESCALA_MAXIMA = 100;

    @Bean
    JacksonModule leituraEndurecida() {
        return new SimpleModule("leitura-endurecida")
                .addDeserializer(BigDecimal.class, new DecimalLimitado())
                .addDeserializer(LocalDateTime.class, new DataHoraLocal());
    }

    /**
     * Recusa decimal com expoente absurdo. 1e-999999999 tem poucos bytes,
     * passa na faixa da latitude e, ao virar texto (chave do cache de rotas)
     * ou coluna, ocupa um gigabyte: um unico request esgota a memoria, e sem
     * memoria o H2 se desliga para todas as lojas (incidente de 17/11). O
     * limite do Jackson e de caracteres lidos, e nao cobre o expoente.
     */
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

    /**
     * O contrato e horario local, sem fuso. O Jackson tambem aceita um "Z"
     * no fim e guarda a hora UTC como se fosse local (3 h a mais, e o dia
     * seguinte depois das 21:00): aqui o "Z" vale o instante que ele diz.
     */
    static final class DataHoraLocal extends LocalDateTimeDeserializer {

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext contexto) {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                String texto = parser.getString().trim();
                if (texto.endsWith("Z")) {
                    try {
                        return Instant.parse(texto).atZone(HorarioLocal.FUSO).toLocalDateTime();
                    } catch (DateTimeException e) {
                        return contexto.reportInputMismatch(LocalDateTime.class, "Data e hora inválida.");
                    }
                }
            }
            return super.deserialize(parser, contexto);
        }
    }
}
