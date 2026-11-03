package br.com.agilizadelivery.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.request.GerarReciboRequest;
import br.com.agilizadelivery.dto.response.ReciboResponse;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;

public interface ReciboService {

    ReciboResponse gerar(UUID estabelecimentoId, UUID usuarioGeradorId, GerarReciboRequest request);

    ReciboResponse buscarPorId(UUID estabelecimentoId, UUID reciboId);

    List<ReciboResponse> listarPorCaixa(UUID estabelecimentoId, UUID caixaId);

    List<ReciboResponse> listarPorEntregadorEStatus(
            UUID estabelecimentoId,
            UUID motoboyId,
            StatusRecibo status);

    List<ReciboResponse> listarPorDataReferencia(UUID estabelecimentoId, LocalDate dataReferencia);

    ReciboResponse marcarComoPago(UUID estabelecimentoId, UUID reciboId, UUID usuarioId);

    ReciboResponse cancelar(UUID estabelecimentoId, UUID reciboId, UUID usuarioId, String motivo);
}
