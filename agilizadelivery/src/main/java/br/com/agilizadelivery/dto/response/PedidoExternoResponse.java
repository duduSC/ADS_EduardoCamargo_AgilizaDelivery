package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.FormaPagamento;
import br.com.agilizadelivery.entity.model.enums.Provedor;

public record PedidoExternoResponse(
        UUID id,
        UUID estabelecimentoId,
        Provedor provedor,
        String idExterno,
        String numeroExibicao,
        String statusExterno,
        String nomeCliente,
        String telefoneCliente,
        BigDecimal valorItens,
        BigDecimal valorTaxaEntrega,
        BigDecimal valorTotal,
        FormaPagamento formaPagamento,
        Boolean pagoOnline,
        BigDecimal trocoPara,
        String observacoes,
        LocalDateTime processadoEm,
        List<ItemPedidoResponse> itens,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {
}
