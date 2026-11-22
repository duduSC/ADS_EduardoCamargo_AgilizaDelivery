package br.com.agilizadelivery.websocket;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** O evento so sai depois do commit: o painel refaz a consulta ao recebe-lo (RNF06). */
class PublicadorStompTempoRealTest {

    private final SimpMessagingTemplate mensageiro = mock(SimpMessagingTemplate.class);
    private final PublicadorStompTempoReal publicador = new PublicadorStompTempoReal(mensageiro);
    private final UUID loja = UUID.randomUUID();
    private String topico;

    @BeforeEach
    void setUp() {
        topico = "/topic/estabelecimento/" + loja + "/entregas";
    }

    @AfterEach
    void limpar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private void abrirTransacao() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @Test
    @DisplayName("Dentro de transação, o evento espera o commit")
    void esperaOCommit() {
        abrirTransacao();

        publicador.publicarEntrega(loja, "CRIADA", null);
        verify(mensageiro, never()).convertAndSend(eq(topico), any(Object.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(mensageiro).convertAndSend(eq(topico), any(Object.class));
    }

    @Test
    @DisplayName("Rollback não publica evento fantasma")
    void rollbackNaoPublica() {
        abrirTransacao();

        publicador.publicarEntrega(loja, "CRIADA", null);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sincronizacao -> sincronizacao.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(mensageiro, never()).convertAndSend(eq(topico), any(Object.class));
    }

    @Test
    @DisplayName("Sem transação, o evento sai na hora")
    void semTransacao() {
        publicador.publicarEntrega(loja, "CRIADA", null);

        verify(mensageiro).convertAndSend(eq(topico), any(Object.class));
    }

    @Test
    @DisplayName("Falha do broker depois do commit não vira erro para quem gravou")
    void falhaDoBroker() {
        doThrow(new MessageDeliveryException("broker fora")).when(mensageiro)
                .convertAndSend(eq(topico), any(Object.class));

        assertThatCode(() -> publicador.publicarEntrega(loja, "CRIADA", null)).doesNotThrowAnyException();
    }
}
