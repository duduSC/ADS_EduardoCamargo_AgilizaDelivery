package br.com.agilizadelivery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import br.com.agilizadelivery.seguranca.CorsProperties;
import br.com.agilizadelivery.websocket.AutenticacaoStompInterceptor;
import br.com.agilizadelivery.websocket.HandshakeValidoInterceptor;
import br.com.agilizadelivery.websocket.SubscricaoTenantInterceptor;
import br.com.agilizadelivery.websocket.TratadorErroStomp;
import tools.jackson.databind.ObjectMapper;

/**
 * WebSocket/STOMP em /ws, com autenticacao JWT no frame CONNECT e
 * isolamento de tenant nas subscricoes (Constituicao 6.1, RNF06). As
 * origens de navegador sao as mesmas do CORS (Constituicao 6.5); cliente
 * sem cabecalho Origin (fora do navegador) nao e afetado.
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
    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;

    public WebSocketConfig(
            AutenticacaoStompInterceptor autenticacaoInterceptor,
            SubscricaoTenantInterceptor subscricaoInterceptor,
            CorsProperties corsProperties,
            ObjectMapper objectMapper) {
        this.autenticacaoInterceptor = autenticacaoInterceptor;
        this.subscricaoInterceptor = subscricaoInterceptor;
        this.corsProperties = corsProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new HandshakeValidoInterceptor(objectMapper))
                .setAllowedOrigins(corsProperties.origensPermitidas().toArray(String[]::new));
        registry.setErrorHandler(new TratadorErroStomp());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(autenticacaoInterceptor, subscricaoInterceptor);
    }
}
