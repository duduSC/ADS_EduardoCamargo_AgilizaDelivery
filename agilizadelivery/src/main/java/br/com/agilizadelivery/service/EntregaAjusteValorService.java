package br.com.agilizadelivery.service;

import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.response.EntregaAjusteValorResponse;

public interface EntregaAjusteValorService {

    List<EntregaAjusteValorResponse> listarPorEntrega(UUID estabelecimentoId, UUID entregaId);
}
