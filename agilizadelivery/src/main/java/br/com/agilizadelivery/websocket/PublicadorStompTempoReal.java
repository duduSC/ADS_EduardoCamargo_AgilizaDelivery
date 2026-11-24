package br.com.agilizadelivery.websocket;

import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.agilizadelivery.dto.response.EntregaResponse;
import br.com.agilizadelivery.dto.response.MotoboyResponse;
import lombok.extern.slf4j.Slf4j;

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
 *
 * O envio espera o commit: o painel refaz a consulta ao receber o evento e
 * leria o dado antigo; e um rollback publicaria o que nao aconteceu.
 */
@Slf4j
@Component
public class PublicadorStompTempoReal implements PublicadorTempoReal {

    private final SimpMessagingTemplate messagingTemplate;

    public PublicadorStompTempoReal(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publicarEntrega(UUID estabelecimentoId, String tipo, EntregaResponse entrega) {
        enviarAposCommit(topicoEntregas(estabelecimentoId), new EventoEntrega(estabelecimentoId, tipo, entrega));
    }

    @Override
    public void publicarFrota(UUID estabelecimentoId, String tipo, MotoboyResponse motoboy) {
        enviarAposCommit(topicoFrota(estabelecimentoId), new EventoFrota(estabelecimentoId, tipo, motoboy));
    }

    private void enviarAposCommit(String topico, Object evento) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            enviar(topico, evento);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enviar(topico, evento);
            }
        });
    }

    /** A escrita ja foi confirmada: falha do broker nao pode virar erro de quem gravou. */
    private void enviar(String topico, Object evento) {
        try {
            messagingTemplate.convertAndSend(topico, evento);
        } catch (RuntimeException e) {
            log.warn("Evento de tempo real não enviado para {}: {}", topico, e.getClass().getSimpleName());
        }
    }

    static String topicoEntregas(UUID estabelecimentoId) {
        return "/topic/estabelecimento/" + estabelecimentoId + "/entregas";
    }

    static String topicoFrota(UUID estabelecimentoId) {
        return "/topic/estabelecimento/" + estabelecimentoId + "/frota";
    }
}
