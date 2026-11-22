package br.com.agilizadelivery.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * Quadro ERROR do STOMP sem detalhe interno (Constituicao 6.4): o padrao
 * do Spring devolve o nome do canal e da classe que recusaram o frame. A
 * sessao e encerrada logo depois, como manda o protocolo.
 */
public class TratadorErroStomp extends StompSubProtocolErrorHandler {

    static final String MENSAGEM = "Conexao recusada ou operacao nao permitida.";

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> mensagemDoCliente, Throwable erro) {
        StompHeaderAccessor quadro = StompHeaderAccessor.create(StompCommand.ERROR);
        quadro.setMessage(MENSAGEM);
        quadro.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], quadro.getMessageHeaders());
    }
}
