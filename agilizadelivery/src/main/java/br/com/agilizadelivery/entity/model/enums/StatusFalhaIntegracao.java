package br.com.agilizadelivery.entity.model.enums;

/**
 * Situacao do registro na fila de erro (RNF04).
 * PENDENTE alimenta o alerta de integracao degradada no painel.
 */
public enum StatusFalhaIntegracao {

    PENDENTE,
    REPROCESSADA,
    DESCARTADA
}
