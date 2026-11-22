package br.com.agilizadelivery.exception.handler;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Substitui a pagina de erro do Spring Boot: o que falha fora do
 * DispatcherServlet (firewall, filtros, sendError) sai no ApiErrorResponse.
 *
 * Da excecao nao se le nada: no container de desenvolvimento o devtools
 * manda a pagina padrao devolver o stacktrace. O log da falha e do servidor.
 */
@RestController
public class ControladorDeErro implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiErrorResponse> responder(HttpServletRequest request) {
        HttpStatusCode status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer codigo
                && codigo >= 400 && codigo < 600
                        ? HttpStatusCode.valueOf(codigo)
                        : HttpStatus.NOT_FOUND; // acesso direto a /error
        String caminho = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String original
                ? original
                : request.getRequestURI();
        return RespostaDeErro.entidade(RespostaDeErro.corpoPadrao(status, caminho));
    }
}
