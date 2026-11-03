package br.com.agilizadelivery.service;

import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.response.PedidoExternoResponse;
import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.entity.model.enums.Provedor;

public interface PedidoExternoService {

    PedidoExternoResponse importar(UUID estabelecimentoId, PedidoExterno pedido);

    PedidoExternoResponse buscarPorId(UUID estabelecimentoId, UUID pedidoExternoId);

    List<PedidoExternoResponse> listarNaoProcessados(UUID estabelecimentoId);

    PedidoExternoResponse marcarProcessado(UUID estabelecimentoId, UUID pedidoExternoId);

    boolean jaImportado(Provedor provedor, String idExterno);
}
