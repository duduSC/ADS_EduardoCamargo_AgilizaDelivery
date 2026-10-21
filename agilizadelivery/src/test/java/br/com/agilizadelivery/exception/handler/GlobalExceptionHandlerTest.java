package br.com.agilizadelivery.exception.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import br.com.agilizadelivery.exception.AgilizaException;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import jakarta.servlet.http.HttpServletRequest;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/caixas/123/fechar");
    }

    @Test
    @DisplayName("Deve tratar AgilizaException mapeando status HTTP e CodigoErro corretamente")
    void deveTratarAgilizaException() {
        AgilizaException ex = new CaixaFechadoException("fechar o caixa");

        ResponseEntity<ApiErrorResponse> resposta = exceptionHandler.tratarAgilizaException(ex, request);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resposta.getStatusCode());
        assertNotNull(resposta.getBody());
        assertEquals(422, resposta.getBody().getStatus());
        assertEquals(CodigoErro.CAIXA_FECHADO, resposta.getBody().getCodigo());
        assertEquals("/api/caixas/123/fechar", resposta.getBody().getPath());
        assertNotNull(resposta.getBody().getTimestamp());
    }

    @Test
    @DisplayName("Deve tratar EntidadeNaoEncontradaException retornando 404 Not Found")
    void deveTratarEntidadeNaoEncontrada() {
        EntidadeNaoEncontradaException ex = new EntidadeNaoEncontradaException("Recurso não encontrado");

        ResponseEntity<ApiErrorResponse> resposta = exceptionHandler.tratarAgilizaException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode());
        assertNotNull(resposta.getBody());
        assertEquals(404, resposta.getBody().getStatus());
        assertEquals(CodigoErro.RECURSO_NAO_ENCONTRADO, resposta.getBody().getCodigo());
    }

    @Test
    @DisplayName("Deve tratar erro generico inesperado retornando 500 sem vazar dados")
    void deveTratarErroInesperado() {
        Exception ex = new RuntimeException("Falha de conexão com ponteiro nulo");

        ResponseEntity<ApiErrorResponse> resposta = exceptionHandler.tratarErroInesperado(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resposta.getStatusCode());
        assertNotNull(resposta.getBody());
        assertEquals(500, resposta.getBody().getStatus());
        assertEquals(CodigoErro.ERRO_INTERNO_SERVIDOR, resposta.getBody().getCodigo());
        assertEquals("Ocorreu um erro interno inesperado no servidor. Por favor, tente novamente mais tarde.",
                resposta.getBody().getMessage());
    }
}
