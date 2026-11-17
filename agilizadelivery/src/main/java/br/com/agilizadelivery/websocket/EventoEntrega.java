package br.com.agilizadelivery.websocket;

import java.util.UUID;

import br.com.agilizadelivery.dto.response.EntregaResponse;

/**
 * Evento de dominio publicado quando uma entrega muda de estado ou e
 * criada. Transporta o DTO pronto para o topico STOMP.
 */
public record EventoEntrega(UUID estabelecimentoId, String tipo, EntregaResponse payload) {
}
