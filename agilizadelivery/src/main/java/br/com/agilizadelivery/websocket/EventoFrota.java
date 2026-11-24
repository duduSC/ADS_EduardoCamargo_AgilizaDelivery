package br.com.agilizadelivery.websocket;

import java.util.UUID;

import br.com.agilizadelivery.dto.response.MotoboyResponse;

/**
 * Evento de dominio publicado quando a posicao ou disponibilidade de
 * um entregador muda.
 */
public record EventoFrota(UUID estabelecimentoId, String tipo, MotoboyResponse payload) {
}
