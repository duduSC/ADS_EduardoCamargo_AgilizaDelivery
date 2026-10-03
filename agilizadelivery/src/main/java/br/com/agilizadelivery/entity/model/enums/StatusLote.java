package br.com.agilizadelivery.entity.model.enums;

/**
 * Maquina de estados do lote de entrega (DVP 1.4.4).
 *
 * DESPACHADO -> EM_ROTA -> CONCLUIDO
 * DESPACHADO -> CANCELADO (devolucao do lote, UC01 A4)
 *
 * O lote nasce DESPACHADO, pois e criado no ato do despacho.
 */
public enum StatusLote {

    DESPACHADO,
    EM_ROTA,
    CONCLUIDO,
    CANCELADO
}
