package br.com.agilizadelivery.entity.model.enums;

/**
 * Disponibilidade do entregador em campo (DVP 1.4.4).
 * Apenas ONLINE pode receber despacho; a telemetria so e coletada
 * em ONLINE ou EM_ROTA (RNF07).
 */
public enum StatusDisponibilidadeMotoboy {

    OFFLINE,
    ONLINE,
    EM_ROTA
}
