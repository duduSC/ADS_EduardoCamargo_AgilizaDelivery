package br.com.agilizadelivery.service;

import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.request.DespacharLoteRequest;
import br.com.agilizadelivery.dto.request.SugerirRotaRequest;
import br.com.agilizadelivery.dto.response.LoteEntregaResponse;
import br.com.agilizadelivery.dto.response.RotaLoteResponse;
import br.com.agilizadelivery.dto.response.SugestaoRotaResponse;
import br.com.agilizadelivery.entity.model.enums.StatusLote;

public interface LoteEntregaService {

    LoteEntregaResponse despachar(UUID estabelecimentoId, UUID usuarioDespachanteId, DespacharLoteRequest request);

    LoteEntregaResponse buscarPorId(UUID estabelecimentoId, UUID loteId);

    List<LoteEntregaResponse> listarPorEstabelecimentoEStatus(UUID estabelecimentoId, StatusLote status);

    List<LoteEntregaResponse> listarPorMotoboy(UUID estabelecimentoId, UUID motoboyId, StatusLote status);

    LoteEntregaResponse iniciarRota(UUID estabelecimentoId, UUID loteId, UUID motoboyId);

    LoteEntregaResponse devolverDespacho(UUID estabelecimentoId, UUID loteId, UUID usuarioId);

    /** Rota principal definida pelo operador, na ordem das paradas. */
    RotaLoteResponse buscarRota(UUID estabelecimentoId, UUID loteId);

    /** Registra a ordem sugerida pelo entregador; a rota oficial nao muda. */
    SugestaoRotaResponse sugerirRota(
            UUID estabelecimentoId,
            UUID loteId,
            UUID motoboyId,
            SugerirRotaRequest request);

    List<SugestaoRotaResponse> listarSugestoes(UUID estabelecimentoId, UUID loteId);
}
