package br.com.agilizadelivery.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.request.AbrirCaixaRequest;
import br.com.agilizadelivery.dto.response.CaixaResponse;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;

public interface CaixaService {

    CaixaResponse abrirCaixa(UUID estabelecimentoId, AbrirCaixaRequest request);

    CaixaResponse buscarPorId(UUID estabelecimentoId, UUID caixaId);

    CaixaResponse buscarPorEstabelecimentoEData(UUID estabelecimentoId, LocalDate dataReferencia);

    List<CaixaResponse> listarPorEstabelecimentoEStatus(UUID estabelecimentoId, StatusCaixa status);

    CaixaResponse fecharCaixa(UUID estabelecimentoId, UUID caixaId, UUID usuarioFechamentoId);

    CaixaResponse reabrirCaixa(
            UUID estabelecimentoId,
            UUID caixaId,
            UUID usuarioReaberturaId,
            String motivoReabertura);
}
