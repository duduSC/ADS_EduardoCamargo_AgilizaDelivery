package br.com.agilizadelivery.service;

import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.response.EntregaStatusHistoricoResponse;

public interface EntregaStatusHistoricoService {

    List<EntregaStatusHistoricoResponse> listarPorEntrega(UUID estabelecimentoId, UUID entregaId);
}
