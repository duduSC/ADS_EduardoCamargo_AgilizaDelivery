package br.com.agilizadelivery.service;

import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.request.CriarEstabelecimentoRequest;
import br.com.agilizadelivery.dto.response.EstabelecimentoResponse;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;

public interface EstabelecimentoService {

    EstabelecimentoResponse criar(CriarEstabelecimentoRequest request);

    EstabelecimentoResponse buscarPorId(UUID estabelecimentoId);

    List<EstabelecimentoResponse> listarPorStatus(StatusEstabelecimento status);

    EstabelecimentoResponse alterarStatus(UUID estabelecimentoId, StatusEstabelecimento status);
}
