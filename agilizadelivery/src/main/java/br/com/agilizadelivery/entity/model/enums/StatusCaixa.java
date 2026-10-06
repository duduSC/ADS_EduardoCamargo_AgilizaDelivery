package br.com.agilizadelivery.entity.model.enums;

/**
 * Maquina de estados do caixa diario (DVP 1.4.4).
 *
 * ABERTO   -> FECHADO  (fechamento do expediente)
 * FECHADO  -> REABERTO (reabertura pelo administrador, com motivo)
 * REABERTO -> FECHADO  (novo fechamento)
 *
 * Enquanto o caixa esta ABERTO ou REABERTO, os recibos do periodo podem
 * ser corrigidos, mesmo os ja pagos. FECHADO torna tudo imutavel.
 */
public enum StatusCaixa {

    ABERTO,
    FECHADO,
    REABERTO
}
