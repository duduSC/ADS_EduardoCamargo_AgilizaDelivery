package br.com.agilizadelivery.websocket;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;

/**
 * Impede que um cliente se inscreva no topico de outro estabelecimento.
 *
 * Padrao: /topic/estabelecimento/{uuid}/... — o UUID deve coincidir com
 * o estabelecimentoId do token autenticado no CONNECT.
 */
@Component
public class SubscricaoTenantInterceptor implements ChannelInterceptor {

    private static final Pattern PADRAO_TOPICO = Pattern.compile(
            "^/topic/estabelecimento/([0-9a-fA-F\\-]{36})/.+$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destino = accessor.getDestination();
            if (destino == null) {
                throw new org.springframework.security.access.AccessDeniedException("Destino nao informado.");
            }
            Matcher matcher = PADRAO_TOPICO.matcher(destino);
            if (matcher.matches()) {
                UUID topicEstabelecimento = UUID.fromString(matcher.group(1));
                UsuarioLogado usuario = extrairUsuario(accessor);
                if (usuario == null || !topicEstabelecimento.equals(usuario.estabelecimentoId())) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "Acesso negado ao topico de outro estabelecimento.");
                }
            }
        }
        return message;
    }

    private static UsuarioLogado extrairUsuario(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            return auth.getPrincipal() instanceof UsuarioLogado u ? u : null;
        }
        return null;
    }
}
