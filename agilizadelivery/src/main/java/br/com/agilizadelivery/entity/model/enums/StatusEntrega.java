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
    CANCELADA;

    /** Indica se a tabela de transicoes admite a mudanca para o destino. */
    public boolean podeTransitarPara(StatusEntrega destino) {
        return switch (this) {
            case AGUARDANDO_DESPACHO -> destino == DESPACHADA || destino == CANCELADA;
            case DESPACHADA -> destino == EM_ROTA || destino == AGUARDANDO_DESPACHO || destino == CANCELADA;
            case EM_ROTA -> destino == ENTREGUE || destino == FALHA || destino == CANCELADA;
            case ENTREGUE, FALHA, CANCELADA -> false;
        };
    }

    public boolean finalizado() {
        return this == ENTREGUE || this == FALHA || this == CANCELADA;
    }
}
