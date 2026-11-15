package br.com.agilizadelivery.integracao.pedidos;

import java.math.BigDecimal;
import java.util.List;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.FormaPagamento;

/**
 * Pedido recebido de um provedor, num formato neutro (DVP 1.5.6, "Dados
 * capturados do provedor"). payloadJson e a resposta original, gravada
 * intacta em PEDIDO_EXTERNO (RNF13).
 *
 * coordenada e opcional: sem ela, o endereco e geocodificado.
 */
public record PedidoRecebido(
        String idExterno,
        String numeroExibicao,
        String statusExterno,
        String nomeCliente,
        String telefoneCliente,
        String enderecoCompleto,
        String bairro,
        String cep,
        String complemento,
        String pontoReferencia,
        Coordenada coordenada,
        BigDecimal valorItens,
        BigDecimal valorTaxaEntrega,
        BigDecimal valorTotal,
        FormaPagamento formaPagamento,
        boolean pagoOnline,
        BigDecimal trocoPara,
        String observacoes,
        List<Item> itens,
        String payloadJson) {

    public record Item(String descricao, short quantidade, BigDecimal valorUnitario, String observacao) {
    }
}
