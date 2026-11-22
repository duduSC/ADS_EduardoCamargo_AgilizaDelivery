package br.com.agilizadelivery.websocket;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import br.com.agilizadelivery.exception.handler.RespostaDeErro;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP comum no /ws responde no ApiErrorResponse (Constituicao 6.4). O
 * handshake do Spring recusa as mesmas requisicoes, mas em texto puro ou
 * sem corpo.
 */
public class HandshakeValidoInterceptor implements HandshakeInterceptor {

    private static final String VERSAO = "13";

    private final ObjectMapper json;

    public HandshakeValidoInterceptor(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> atributos) throws IOException {
        HttpStatus recusa = null;
        if (!HttpMethod.GET.equals(request.getMethod())) {
            recusa = HttpStatus.METHOD_NOT_ALLOWED;
        } else if (!pedeWebSocket(request)) {
            recusa = HttpStatus.BAD_REQUEST;
        }
        if (recusa == null || !(response instanceof ServletServerHttpResponse daServlet)) {
            return recusa == null;
        }
        HttpServletResponse resposta = daServlet.getServletResponse();
        if (recusa == HttpStatus.METHOD_NOT_ALLOWED) {
            resposta.setHeader("Allow", HttpMethod.GET.name());
        }
        RespostaDeErro.escrever(json, resposta, RespostaDeErro.corpoPadrao(recusa, request.getURI().getRawPath()));
        return false;
    }

    private static boolean pedeWebSocket(ServerHttpRequest request) {
        var cabecalhos = request.getHeaders();
        String chave = cabecalhos.getFirst("Sec-WebSocket-Key");
        return "websocket".equalsIgnoreCase(cabecalhos.getUpgrade())
                && cabecalhos.getConnection().stream()
                        .anyMatch(valor -> valor.toLowerCase(Locale.ROOT).contains("upgrade"))
                && VERSAO.equals(cabecalhos.getFirst("Sec-WebSocket-Version"))
                && chave != null && !chave.isBlank();
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception falha) {
        // nada a desfazer
    }
}
