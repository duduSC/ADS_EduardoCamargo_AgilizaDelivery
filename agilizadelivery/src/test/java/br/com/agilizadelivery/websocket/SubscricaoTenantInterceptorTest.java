package br.com.agilizadelivery.websocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.CanalAcesso;

/**
 * Testa que o interceptor de subscricao impede inscricao no topico
 * de outro estabelecimento.
 */
class SubscricaoTenantInterceptorTest {

    private SubscricaoTenantInterceptor interceptor;
    private UUID estabelecimentoDoUsuario;

    @BeforeEach
    void setUp() {
        interceptor = new SubscricaoTenantInterceptor();
        estabelecimentoDoUsuario = UUID.randomUUID();
    }

    private Message<?> subscribeMessage(String destino) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destino);
        UsuarioLogado usuario = new UsuarioLogado(
                UUID.randomUUID(), estabelecimentoDoUsuario, PerfilUsuario.OPERADOR, CanalAcesso.BACKOFFICE);
        accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_OPERADOR"))));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("Inscricao no topico do proprio estabelecimento: aceita")
    void deveAceitarProprioEstabelecimento() {
        String destino = "/topic/estabelecimento/" + estabelecimentoDoUsuario + "/entregas";
        assertDoesNotThrow(() -> interceptor.preSend(subscribeMessage(destino), null));
    }

    @Test
    @DisplayName("Inscricao no topico de outro estabelecimento: recusa")
    void deveRecusarOutroEstabelecimento() {
        String destino = "/topic/estabelecimento/" + UUID.randomUUID() + "/entregas";
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(subscribeMessage(destino), null));
    }

    @Test
    @DisplayName("Inscricao no topico /frota do proprio estabelecimento: aceita")
    void deveAceitarFrotaProprioEstabelecimento() {
        String destino = "/topic/estabelecimento/" + estabelecimentoDoUsuario + "/frota";
        assertDoesNotThrow(() -> interceptor.preSend(subscribeMessage(destino), null));
    }
}
