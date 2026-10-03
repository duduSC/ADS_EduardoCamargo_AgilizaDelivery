package br.com.agilizadelivery.entity.model.enums;

/**
 * Passo do ciclo do worker em que a falha ocorreu (DVP 1.5.6).
 */
public enum EtapaFalhaIntegracao {

    AUTENTICACAO,
    CONSULTA,
    CONFIRMACAO,
    GEOCODIFICACAO,
    PERSISTENCIA
}
