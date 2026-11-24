package br.com.agilizadelivery.seguranca;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import br.com.agilizadelivery.exception.TokenInvalidoException;
import br.com.agilizadelivery.exception.handler.CodigoErro;
import br.com.agilizadelivery.exception.handler.RespostaDeErro;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * 401 e 403 gerados pela cadeia do Spring Security, fora dos controllers,
 * no mesmo formato ApiErrorResponse do GlobalExceptionHandler e sem
 * detalhe interno (Constituicao 6.4).
 */
@Component
public class RespostaErroSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RespostaErroSeguranca(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        escrever(request, response, HttpStatus.UNAUTHORIZED, CodigoErro.NAO_AUTENTICADO, TokenInvalidoException.MENSAGEM);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        escrever(request, response, HttpStatus.FORBIDDEN, CodigoErro.ACESSO_NEGADO,
                "Você não tem permissão para acessar este recurso.");
    }

    /** Origem fora da lista do CORS (Constituicao 6.5). */
    void origemRecusada(HttpServletRequest request, HttpServletResponse response) throws IOException {
        escrever(request, response, HttpStatus.FORBIDDEN, CodigoErro.ACESSO_NEGADO,
                "Esta origem não tem permissão para acessar a API.");
    }

    private void escrever(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            CodigoErro codigo, String mensagem) throws IOException {
        RespostaDeErro.escrever(objectMapper, response,
                RespostaDeErro.corpo(status, codigo, mensagem, request.getRequestURI()));
    }
}
