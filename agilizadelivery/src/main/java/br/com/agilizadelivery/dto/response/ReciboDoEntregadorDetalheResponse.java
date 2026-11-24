package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.StatusRecibo;

/**
 * Recibo do entregador com as entregas que ele consolida (D013): os campos
 * da lista (ReciboDoEntregadorResponse) mais as entregas.
 *
 * Um recibo CANCELADO tem a lista vazia: o cancelamento desvincula as
 * entregas (UC03 A2), que voltam a ficar disponiveis para outro acerto.
 * qtdEntregas e valorTotal continuam os da emissao.
 */
public record ReciboDoEntregadorDetalheResponse(
        UUID id,
        LocalDate dataReferencia,
        LocalDateTime periodoInicio,
        LocalDateTime periodoFim,
        Short qtdEntregas,
        BigDecimal valorTotal,
        StatusRecibo status,
        LocalDateTime pagoEm,
        LocalDateTime criadoEm,
        List<EntregaDoReciboResponse> entregas) {

    public static ReciboDoEntregadorDetalheResponse de(
            ReciboDoEntregadorResponse recibo, List<EntregaDoReciboResponse> entregas) {
        return new ReciboDoEntregadorDetalheResponse(
                recibo.id(),
                recibo.dataReferencia(),
                recibo.periodoInicio(),
                recibo.periodoFim(),
                recibo.qtdEntregas(),
                recibo.valorTotal(),
                recibo.status(),
                recibo.pagoEm(),
                recibo.criadoEm(),
                entregas);
    }
}
