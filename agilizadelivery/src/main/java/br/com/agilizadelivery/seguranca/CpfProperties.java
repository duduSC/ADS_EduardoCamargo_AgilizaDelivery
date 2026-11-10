package br.com.agilizadelivery.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chaves de protecao do CPF, em Base64. Vem do ambiente, nunca do banco.
 *
 * chaveCifra: 32 bytes (AES-256). chaveHash: ao menos 32 bytes (HMAC-SHA256).
 */
@ConfigurationProperties(prefix = "agiliza.seguranca.cpf")
public record CpfProperties(String chaveCifra, String chaveHash) {
}
