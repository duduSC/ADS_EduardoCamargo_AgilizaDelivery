package br.com.agilizadelivery.websocket;

import java.util.UUID;

import br.com.agilizadelivery.dto.response.EntregaResponse;
import br.com.agilizadelivery.dto.response.MotoboyResponse;

/**
 * Interface de publicacao de eventos em tempo real. Desacopla o dominio
 * da tecnologia de transporte (STOMP).
 */
public interface PublicadorTempoReal {

    void publicarEntrega(UUID estabelecimentoId, String tipo, EntregaResponse entrega);

    void publicarFrota(UUID estabelecimentoId, String tipo, MotoboyResponse motoboy);
}
