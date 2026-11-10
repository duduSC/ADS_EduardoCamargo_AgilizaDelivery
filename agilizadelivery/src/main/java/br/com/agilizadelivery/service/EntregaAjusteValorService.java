package br.com.agilizadelivery.service;

import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.request.AjustarRepasseRequest;
import br.com.agilizadelivery.dto.response.EntregaAjusteValorResponse;

public interface EntregaAjusteValorService {

    /**
     * Ajuste manual do repasse (UC03 A1). Recusado se a entrega estiver em
     * recibo, GERADO ou PAGO, ou se o caixa do dia nao estiver aberto.
     */
    EntregaAjusteValorResponse ajustar(
            UUID estabelecimentoId,
            UUID entregaId,
            UUID usuarioId,
            AjustarRepasseRequest request);

    List<EntregaAjusteValorResponse> listarPorEntrega(UUID estabelecimentoId, UUID entregaId);
}
