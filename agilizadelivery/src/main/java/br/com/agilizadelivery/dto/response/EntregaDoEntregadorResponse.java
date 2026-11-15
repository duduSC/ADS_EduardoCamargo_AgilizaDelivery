package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.entity.model.enums.FormaPagamento;
import br.com.agilizadelivery.entity.model.enums.MotivoRevisao;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

/**
 * Entrega vista pelo aplicativo do entregador (UC02, passo 2): endereco,
 * contato, itens, valor e forma de pagamento. Nao expoe dados internos da
 * loja (faixa, recibo, despachante). Entregas avulsas trazem itens vazios;
 * o conteudo delas fica em observacoes.
 */
public record EntregaDoEntregadorResponse(
        UUID id,
        UUID loteId,
        Short ordemRota,
        StatusEntrega status,
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
        String motivoFalha,
        LocalDateTime finalizadaEm,
        Boolean pendenteRevisao,
        MotivoRevisao motivoRevisao,
        List<ItemDoEntregadorResponse> itens) {
}
