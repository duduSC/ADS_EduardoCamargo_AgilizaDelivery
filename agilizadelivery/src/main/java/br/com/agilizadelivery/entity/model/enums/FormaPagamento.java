package br.com.agilizadelivery.entity.model.enums;

/**
 * Forma de pagamento do pedido (DVP 1.5.4).
 * Combinada com pagoOnline, informa ao entregador se ha valor
 * a receber na porta.
 */
public enum FormaPagamento {

    DINHEIRO,
    CREDITO,
    DEBITO,
    PIX,
    VALE
}
