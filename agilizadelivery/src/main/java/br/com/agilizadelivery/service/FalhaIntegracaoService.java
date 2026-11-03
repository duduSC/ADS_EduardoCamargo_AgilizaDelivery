package br.com.agilizadelivery.service;

import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.response.FalhaIntegracaoResponse;
import br.com.agilizadelivery.entity.model.enums.StatusFalhaIntegracao;

public interface FalhaIntegracaoService {

    List<FalhaIntegracaoResponse> listarPorStatus(UUID estabelecimentoId, StatusFalhaIntegracao status);

    long contarPorStatus(UUID estabelecimentoId, StatusFalhaIntegracao status);

    FalhaIntegracaoResponse marcarReprocessada(UUID estabelecimentoId, Long falhaId);

    FalhaIntegracaoResponse descartar(UUID estabelecimentoId, Long falhaId);
}
