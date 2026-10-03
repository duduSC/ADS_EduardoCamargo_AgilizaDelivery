package br.com.agilizadelivery.entity.model.enums;

/**
 * Maquina de estados da entrega (DVP 1.4.4).
 *
 * AGUARDANDO_DESPACHO -> DESPACHADA, CANCELADA
 * DESPACHADA          -> EM_ROTA, AGUARDANDO_DESPACHO (devolucao), CANCELADA
 * EM_ROTA             -> ENTREGUE, FALHA, CANCELADA
 * ENTREGUE, FALHA, CANCELADA sao estados finais.
 */
public enum StatusEntrega {

    AGUARDANDO_DESPACHO,
    DESPACHADA,
    EM_ROTA,
    ENTREGUE,
    FALHA,
    CANCELADA
}
