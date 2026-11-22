package br.com.agilizadelivery.seguranca;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Origem recusada pelo CORS responde no ApiErrorResponse; o padrao do
 * Spring e o texto "Invalid CORS request".
 */
public class ProcessadorCorsPadronizado extends DefaultCorsProcessor {

    private final RespostaErroSeguranca respostaErro;

    public ProcessadorCorsPadronizado(RespostaErroSeguranca respostaErro) {
        this.respostaErro = respostaErro;
    }

    @Override
    public boolean processRequest(CorsConfiguration configuracao, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        boolean aceita = super.processRequest(configuracao, request, response);
        if (!aceita) {
            respostaErro.origemRecusada(request, response);
        }
        return aceita;
    }

    @Override
    protected void rejectRequest(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN); // o corpo sai em processRequest
    }
}
