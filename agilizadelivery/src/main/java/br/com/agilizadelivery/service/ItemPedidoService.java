package br.com.agilizadelivery.service;

import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.response.ItemPedidoResponse;

public interface ItemPedidoService {

    List<ItemPedidoResponse> listarPorPedido(UUID estabelecimentoId, UUID pedidoExternoId);
}
