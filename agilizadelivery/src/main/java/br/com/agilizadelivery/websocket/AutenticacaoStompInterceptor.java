package br.com.agilizadelivery.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import br.com.agilizadelivery.exception.TokenInvalidoException;
import br.com.agilizadelivery.seguranca.jwt.FiltroAutenticacaoJwt;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;

/**
 * Valida o JWT na abertura da sessao STOMP (Constituicao 6.1): o frame
 * CONNECT ou o seu alias STOMP, que o Spring trata igual (STOMP 1.2).
 *
 * O token vem no cabecalho nativo "Authorization: Bearer <jwt>" e sai da
 * mensagem assim que e lido, para nao acompanha-la ate o broker nem ate
 * um log. Sem token valido a conexao e recusada com AccessDeniedException.
 */
@Component
public class AutenticacaoStompInterceptor implements ChannelInterceptor {

    private static final String AUTORIZACAO = "Authorization";
    private static final String PREFIXO = "Bearer ";

    private final ServicoJwt servicoJwt;

    public AutenticacaoStompInterceptor(ServicoJwt servicoJwt) {
        this.servicoJwt = servicoJwt;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && abreSessao(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader(AUTORIZACAO);
            if (accessor.isMutable()) {
                accessor.removeNativeHeader(AUTORIZACAO);
            }
            if (header == null || !header.regionMatches(true, 0, PREFIXO, 0, PREFIXO.length())) {
                throw new AccessDeniedException("Token ausente.");
            }
            try {
                UsuarioLogado usuario = servicoJwt.validarTokenAcesso(header.substring(PREFIXO.length()));
                accessor.setUser(FiltroAutenticacaoJwt.autenticacao(usuario));
            } catch (TokenInvalidoException e) {
                throw new AccessDeniedException("Token invalido.");
            }
        }
        return message;
    }

    private static boolean abreSessao(StompCommand comando) {
        return comando == StompCommand.CONNECT || comando == StompCommand.STOMP;
    }
}
