package br.com.agilizadelivery.seguranca;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import br.com.agilizadelivery.service.seguranca.ProtetorCpf;

/**
 * CPF cifrado com AES-256-GCM (IV aleatorio de 12 bytes prefixado ao
 * texto cifrado, tudo em Base64) e indexado por HMAC-SHA256.
 */
@Component
public class AesGcmProtetorCpf implements ProtetorCpf {

    private static final String CIFRA = "AES/GCM/NoPadding";
    private static final String HMAC = "HmacSHA256";
    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;

    private final SecretKeySpec chaveCifra;
    private final SecretKeySpec chaveHash;
    private final SecureRandom aleatorio = new SecureRandom();

    public AesGcmProtetorCpf(CpfProperties properties) {
        byte[] cifra = decodificar(properties.chaveCifra(), "chave-cifra");
        byte[] hash = decodificar(properties.chaveHash(), "chave-hash");
        if (cifra.length != 32) {
            throw new IllegalStateException("agiliza.seguranca.cpf.chave-cifra deve ter 32 bytes (AES-256).");
        }
        if (hash.length < 32) {
            throw new IllegalStateException("agiliza.seguranca.cpf.chave-hash deve ter ao menos 32 bytes.");
        }
        this.chaveCifra = new SecretKeySpec(cifra, "AES");
        this.chaveHash = new SecretKeySpec(hash, HMAC);
    }

    @Override
    public String cifrar(String cpf) {
        try {
            byte[] iv = new byte[TAMANHO_IV];
            aleatorio.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIFRA);
            cipher.init(Cipher.ENCRYPT_MODE, chaveCifra, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(ProtetorCpf.normalizar(cpf).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar o CPF.", e);
        }
    }

    @Override
    public String decifrar(String cpfCifrado) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(cpfCifrado));
            byte[] iv = new byte[TAMANHO_IV];
            buffer.get(iv);
            byte[] cifrado = new byte[buffer.remaining()];
            buffer.get(cifrado);
            Cipher cipher = Cipher.getInstance(CIFRA);
            cipher.init(Cipher.DECRYPT_MODE, chaveCifra, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Falha ao decifrar o CPF.", e);
        }
    }

    @Override
    public String hash(String cpf) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(chaveHash);
            return HexFormat.of().formatHex(
                    mac.doFinal(ProtetorCpf.normalizar(cpf).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao gerar o hash do CPF.", e);
        }
    }

    private static byte[] decodificar(String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("agiliza.seguranca.cpf." + nome + " nao configurada.");
        }
        return Base64.getDecoder().decode(valor);
    }
}
