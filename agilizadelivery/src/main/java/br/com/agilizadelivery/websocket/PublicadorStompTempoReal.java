package br.com.agilizadelivery.websocket;

import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import br.com.agilizadelivery.dto.response.EntregaResponse;
import br.com.agilizadelivery.dto.response.MotoboyResponse;

/**
 * Implementacao STOMP do publicador de eventos em tempo real.
 *
 * Topicos:
 * - /topic/estabelecimento/{id}/entregas
 * - /topic/estabelecimento/{id}/frota
 *
 * Cada mensagem leva o "tipo" (CRIADA, STATUS_ALTERADO, DESPACHADA,
 * DEVOLVIDA, POSICAO_ATUALIZADA, DISPONIBILIDADE_ALTERADA) como campo
 * do payload, para o front decidir o que redesenhar.
 */
@Component
public class PublicadorStompTempoReal implements PublicadorTempoReal {

    private final SimpMessagingTemplate messagingTemplate;

    public PublicadorStompTempoReal(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publicarEntrega(UUID estabelecimentoId, String tipo, EntregaResponse entrega) {
        String destino = "/topic/estabelecimento/" + estabelecimentoId + "/entregas";
        messagingTemplate.convertAndSend(destino, new EventoEntrega(estabelecimentoId, tipo, entrega));
    }

    @Override
    public void publicarFrota(UUID estabelecimentoId, String tipo, MotoboyResponse motoboy) {
        String destino = "/topic/estabelecimento/" + estabelecimentoId + "/frota";
        messagingTemplate.convertAndSend(destino, new EventoFrota(estabelecimentoId, tipo, motoboy));
    }
}
