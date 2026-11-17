package br.com.agilizadelivery.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import br.com.agilizadelivery.exception.TokenInvalidoException;
import br.com.agilizadelivery.seguranca.jwt.FiltroAutenticacaoJwt;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;

/**
 * Valida o JWT no frame CONNECT do STOMP (Constituicao 6.1).
 *
 * O token vem no cabecalho nativo "Authorization: Bearer <jwt>". Sem
 * token valido a conexao e recusada com AccessDeniedException.
 */
@Component
public class AutenticacaoStompInterceptor implements ChannelInterceptor {

    private static final String PREFIXO = "Bearer ";

    private final ServicoJwt servicoJwt;

    public AutenticacaoStompInterceptor(ServicoJwt servicoJwt) {
        this.servicoJwt = servicoJwt;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.regionMatches(true, 0, PREFIXO, 0, PREFIXO.length())) {
                throw new org.springframework.security.access.AccessDeniedException("Token ausente.");
            }
            try {
                UsuarioLogado usuario = servicoJwt.validarTokenAcesso(header.substring(PREFIXO.length()));
                accessor.setUser(FiltroAutenticacaoJwt.autenticacao(usuario));
            } catch (TokenInvalidoException e) {
                throw new org.springframework.security.access.AccessDeniedException("Token invalido.");
            }
        }
        return message;
    }
}
