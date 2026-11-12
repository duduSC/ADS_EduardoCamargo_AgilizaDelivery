package br.com.agilizadelivery.websocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.seguranca.jwt.JwtProperties;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;
import br.com.agilizadelivery.service.CanalAcesso;

/**
 * Testa que o interceptor STOMP exige JWT valido no CONNECT e
 * recusa conexoes sem token ou com token invalido.
 */
class AutenticacaoStompInterceptorTest {

    private static final String SEGREDO_B64 = "UQYEmGZe0cmQNCw5EA7y5LrOP10eB3PaBM2+jM7CwlGtoufVjl53W/hLXM8Mreve";

    private ServicoJwt servicoJwt;
    private AutenticacaoStompInterceptor interceptor;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SEGREDO_B64,
                java.time.Duration.ofHours(3), java.time.Duration.ofDays(30), "agiliza-test");
        servicoJwt = new ServicoJwt(props);
        interceptor = new AutenticacaoStompInterceptor(servicoJwt);
    }

    private Message<?> connectMessage(String authValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authValue != null) {
            accessor.addNativeHeader("Authorization", authValue);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("CONNECT com token valido: aceita")
    void deveAceitarTokenValido() {
        UUID estId = UUID.randomUUID();
        String token = servicoJwt.gerarTokenAcesso(
                new UsuarioAutenticadoResponse(UUID.randomUUID(), estId, null, "Operador",
                        PerfilUsuario.OPERADOR),
                CanalAcesso.BACKOFFICE);

        Message<?> msg = connectMessage("Bearer " + token);
        assertDoesNotThrow(() -> interceptor.preSend(msg, null));
    }

    @Test
    @DisplayName("CONNECT sem header Authorization: recusa")
    void deveRecusarSemToken() {
        Message<?> msg = connectMessage(null);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(msg, null));
    }

    @Test
    @DisplayName("CONNECT com token invalido: recusa")
    void deveRecusarTokenInvalido() {
        Message<?> msg = connectMessage("Bearer token-invalido");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(msg, null));
    }
}
