package br.com.agilizadelivery.service.seguranca;

/**
 * Protecao do CPF do entregador em repouso (RNF02 e RNF07).
 *
 * cifrar produz um valor diferente a cada chamada; por isso a unicidade
 * e verificada pelo hash, um indice cego deterministico.
 */
public interface ProtetorCpf {

    String cifrar(String cpf);

    String decifrar(String cpfCifrado);

    /** Hash deterministico do CPF, com 64 caracteres hexadecimais. */
    String hash(String cpf);

    /** Mantem apenas os digitos, para que 123.456.789-09 e 12345678909 coincidam. */
    static String normalizar(String cpf) {
        return cpf == null ? null : cpf.replaceAll("\\D", "");
    }
}
