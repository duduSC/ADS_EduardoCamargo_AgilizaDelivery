package br.com.agilizadelivery.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.request.AlterarStatusEntregaRequest;
import br.com.agilizadelivery.dto.request.CriarEntregaAvulsaRequest;
import br.com.agilizadelivery.dto.response.EntregaResponse;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

public interface EntregaService {

    EntregaResponse criarAvulsa(UUID estabelecimentoId, CriarEntregaAvulsaRequest request);

    EntregaResponse buscarPorId(UUID estabelecimentoId, UUID entregaId);

    List<EntregaResponse> listarPorEstabelecimentoEStatus(UUID estabelecimentoId, StatusEntrega status);

    List<EntregaResponse> listarPendentesDeDespacho(UUID estabelecimentoId);

    List<EntregaResponse> listarDoMotoboy(
            UUID estabelecimentoId,
            UUID motoboyId,
            LocalDateTime inicio,
            LocalDateTime fim);

    EntregaResponse marcarProntaParaDespacho(UUID estabelecimentoId, UUID entregaId, LocalDateTime prontaEm);

    EntregaResponse alterarStatus(
            UUID estabelecimentoId,
            UUID entregaId,
            UUID usuarioId,
            AlterarStatusEntregaRequest request);

    EntregaResponse cancelar(UUID estabelecimentoId, UUID entregaId, UUID usuarioId, String motivo);

    EntregaResponse ajustarCoordenada(
            UUID estabelecimentoId,
            UUID entregaId,
            BigDecimal latitude,
            BigDecimal longitude,
            UUID usuarioId);

    EntregaResponse contestar(UUID estabelecimentoId, UUID entregaId, UUID motoboyId);

    EntregaResponse resolverRevisao(
            UUID estabelecimentoId,
            UUID entregaId,
            UUID usuarioId,
            BigDecimal valorRepasse,
            String motivo);
}
