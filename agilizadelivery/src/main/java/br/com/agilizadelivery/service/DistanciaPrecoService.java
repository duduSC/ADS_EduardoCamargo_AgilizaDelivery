package br.com.agilizadelivery.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.request.SalvarDistanciaPrecoRequest;
import br.com.agilizadelivery.dto.response.DistanciaPrecoResponse;

public interface DistanciaPrecoService {

    List<DistanciaPrecoResponse> substituirTabela(
            UUID estabelecimentoId,
            List<SalvarDistanciaPrecoRequest> faixas);

    List<DistanciaPrecoResponse> listarFaixasAtivas(UUID estabelecimentoId);

    List<DistanciaPrecoResponse> listarHistorico(UUID estabelecimentoId);

    DistanciaPrecoResponse buscarFaixaParaDistancia(UUID estabelecimentoId, BigDecimal distanciaKm);
}
