package br.com.agilizadelivery.entity.model.enums;

/**
 * Origem dos pedidos importados (DVP 1.5.6).
 * SIMULADOR gera pedidos sinteticos no mesmo contrato do IFOOD,
 * mitigando o risco R01 (dependencia da homologacao).
 */
public enum Provedor {

    IFOOD,
    SIMULADOR
}
