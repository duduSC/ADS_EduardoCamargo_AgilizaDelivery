package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.entity.model.enums.FormaPagamento;
import br.com.agilizadelivery.entity.model.enums.MotivoRevisao;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

public record EntregaResponse(
        UUID id,
        UUID estabelecimentoId,
        UUID pedidoExternoId,
        UUID loteId,
        UUID faixaPrecoId,
        UUID reciboId,
        String nomeCliente,
        String telefoneCliente,
        String enderecoCompleto,
        String bairro,
        String cep,
        String complemento,
        String pontoReferencia,
        CoordenadaDto coordenada,
        BigDecimal valorPedido,
        FormaPagamento formaPagamento,
        Boolean pagoOnline,
        BigDecimal trocoPara,
        String observacoes,
        BigDecimal distanciaKm,
        BigDecimal valorRepasse,
        StatusEntrega status,
        String motivoFalha,
        Boolean pendenteRevisao,
        MotivoRevisao motivoRevisao,
        LocalDateTime prontoParaDespachoEm,
        LocalDateTime despachadaEm,
        LocalDateTime finalizadaEm,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {
}
