package br.com.agilizadelivery.websocket;

import java.util.Set;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.CanalAcesso;

/**
 * O que um cliente pode fazer no /ws (Constituicao 5.6). E uma lista do
 * que e permitido, porque o broker simples entende padroes na inscricao
 * (/topic/**) e repassa o que o cliente publicar em /topic.
 *
 * - so os frames de sessao e de inscricao: cliente nunca publica (nao ha
 *   destino de aplicacao), seja com SEND, seja com MESSAGE;
 * - inscricao so nos topicos exatos do estabelecimento do token, sem
 *   padrao: /topic/estabelecimento/{id}/entregas e /frota;
 * - PROVISORIO (T014): so o painel da loja (OPERADOR e ADMIN_RESTAURANTE
 *   no BACKOFFICE, RNF06) assina. Esses topicos levam dados de clientes de
 *   todas as entregas da loja, e o aplicativo nao usa WebSocket (D008).
 */
@Component
public class SubscricaoTenantInterceptor implements ChannelInterceptor {

    private static final Set<PerfilUsuario> PERFIS_DO_PAINEL =
            Set.of(PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE);

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // batimento
        }
        StompCommand comando = accessor.getCommand();
        switch (comando) {
            case CONNECT, STOMP, UNSUBSCRIBE, DISCONNECT -> {
                return message;
            }
            case SUBSCRIBE -> {
                exigirTopicoProprio(extrairUsuario(accessor), accessor.getDestination());
                return message;
            }
            default -> throw new AccessDeniedException("Operacao STOMP nao permitida: " + comando);
        }
    }

    private static void exigirTopicoProprio(UsuarioLogado usuario, String destino) {
        boolean painelDaLoja = usuario != null
                && usuario.estabelecimentoId() != null
                && usuario.canal() == CanalAcesso.BACKOFFICE
                && PERFIS_DO_PAINEL.contains(usuario.perfil());
        if (!painelDaLoja || destino == null || !topicosDaLoja(usuario).contains(destino)) {
            throw new AccessDeniedException("Inscricao nao permitida neste topico.");
        }
    }

    private static Set<String> topicosDaLoja(UsuarioLogado usuario) {
        return Set.of(
                PublicadorStompTempoReal.topicoEntregas(usuario.estabelecimentoId()),
                PublicadorStompTempoReal.topicoFrota(usuario.estabelecimentoId()));
    }

    private static UsuarioLogado extrairUsuario(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            return auth.getPrincipal() instanceof UsuarioLogado u ? u : null;
        }
        return null;
    }
}
