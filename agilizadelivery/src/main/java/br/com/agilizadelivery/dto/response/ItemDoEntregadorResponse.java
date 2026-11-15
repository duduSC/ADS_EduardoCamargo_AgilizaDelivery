package br.com.agilizadelivery.dto.response;

/**
 * Item do pedido, para o entregador conferir a sacola (UC02, passo 2).
 */
public record ItemDoEntregadorResponse(
        String descricao,
        Short quantidade,
        String observacao) {
}
