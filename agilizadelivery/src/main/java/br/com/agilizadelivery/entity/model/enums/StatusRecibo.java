package br.com.agilizadelivery.entity.model.enums;

/**
 * Maquina de estados do recibo de acerto (DVP 1.4.4).
 *
 * GERADO -> PAGO (UC03, passo 6)
 * GERADO -> CANCELADO (UC03 A2), liberando as entregas para novo acerto.
 * Recibos PAGOS nao podem ser cancelados.
 */
public enum StatusRecibo {

    GERADO,
    PAGO,
    CANCELADO
}
