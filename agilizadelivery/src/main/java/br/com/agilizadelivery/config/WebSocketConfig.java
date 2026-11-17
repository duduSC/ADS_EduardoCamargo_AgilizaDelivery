package br.com.agilizadelivery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import br.com.agilizadelivery.websocket.AutenticacaoStompInterceptor;
import br.com.agilizadelivery.websocket.SubscricaoTenantInterceptor;

/**
 * WebSocket/STOMP em /ws, com autenticacao JWT no frame CONNECT e
 * isolamento de tenant nas subscricoes (Constituicao 6.1, RNF06).
 *
 * Topicos escopados ao estabelecimento:
 * - /topic/estabelecimento/{id}/entregas  (nova entrega, mudanca de status)
 * - /topic/estabelecimento/{id}/frota     (posicao e disponibilidade)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AutenticacaoStompInterceptor autenticacaoInterceptor;
    private final SubscricaoTenantInterceptor subscricaoInterceptor;

    public WebSocketConfig(
            AutenticacaoStompInterceptor autenticacaoInterceptor,
            SubscricaoTenantInterceptor subscricaoInterceptor) {
        this.autenticacaoInterceptor = autenticacaoInterceptor;
        this.subscricaoInterceptor = subscricaoInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(autenticacaoInterceptor, subscricaoInterceptor);
    }
}
