package br.com.agilizadelivery.seguranca.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do JWT (D002).
 *
 * segredo: chave HMAC em Base64, com ao menos 32 bytes (HS256). Vem do
 * ambiente (JWT_SECRET), nunca do repositorio.
 * accessTtl: validade do token de acesso (padrao 3 h).
 * refreshTtl: validade do refresh token (padrao 30 dias, PROVISORIO: o
 * refresh token nao existe no DVP 3.1).
 */
@ConfigurationProperties(prefix = "agiliza.jwt")
public record JwtProperties(String segredo, Duration accessTtl, Duration refreshTtl, String emissor) {

    public JwtProperties {
        if (accessTtl == null) {
            accessTtl = Duration.ofHours(3);
        }
        if (refreshTtl == null) {
            refreshTtl = Duration.ofDays(30);
        }
        if (emissor == null || emissor.isBlank()) {
            emissor = "agilizadelivery";
        }
    }
}
