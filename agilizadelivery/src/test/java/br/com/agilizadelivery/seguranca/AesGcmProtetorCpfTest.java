package br.com.agilizadelivery.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmProtetorCpfTest {

    private static final String CHAVE_32 = Base64.getEncoder().encodeToString(new byte[32]);

    private AesGcmProtetorCpf protetor;

    @BeforeEach
    void setUp() {
        byte[] hash = new byte[32];
        hash[0] = 7;
        protetor = new AesGcmProtetorCpf(new CpfProperties(CHAVE_32, Base64.getEncoder().encodeToString(hash)));
    }

    @Test
    @DisplayName("Deve cifrar com IV aleatório e decifrar o CPF normalizado")
    void deveCifrarEDecifrar() {
        String primeiro = protetor.cifrar("123.456.789-09");
        String segundo = protetor.cifrar("12345678909");

        assertNotEquals(primeiro, segundo);
        assertFalse(primeiro.contains("12345678909"));
        assertEquals("12345678909", protetor.decifrar(primeiro));
        assertEquals("12345678909", protetor.decifrar(segundo));
    }

    @Test
    @DisplayName("Deve gerar hash determinístico de 64 caracteres, igual com ou sem máscara")
    void deveGerarIndiceCego() {
        String hash = protetor.hash("123.456.789-09");

        assertEquals(hash, protetor.hash("12345678909"));
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertNotEquals(hash, protetor.hash("98765432100"));
    }

    @Test
    @DisplayName("Deve recusar chaves ausentes ou de tamanho inválido")
    void deveValidarChaves() {
        String curta = Base64.getEncoder().encodeToString(new byte[16]);

        assertThrows(IllegalStateException.class, () -> new AesGcmProtetorCpf(new CpfProperties(null, CHAVE_32)));
        assertThrows(IllegalStateException.class, () -> new AesGcmProtetorCpf(new CpfProperties(curta, CHAVE_32)));
        assertThrows(IllegalStateException.class, () -> new AesGcmProtetorCpf(new CpfProperties(CHAVE_32, curta)));
    }

    @Test
    @DisplayName("Deve falhar ao decifrar valor adulterado")
    void deveDetectarAdulteracao() {
        byte[] cifrado = Base64.getDecoder().decode(protetor.cifrar("12345678909"));
        cifrado[cifrado.length - 1] ^= 1;

        assertThrows(IllegalStateException.class, () ->
                protetor.decifrar(Base64.getEncoder().encodeToString(cifrado)));
    }
}
