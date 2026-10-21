package br.com.agilizadelivery.exception.handler;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.com.agilizadelivery.exception.AgilizaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Interceptor global de excecoes da aplicacao (AOP / Controller Advice Pattern).
 *
 * Centraliza a traducao de excecoes lancadas pela camada Service e pela infraestrutura
 * do Spring em respostas HTTP estruturadas (ApiErrorResponse), garantindo contratos
 * previsiveis para o cliente Web (React) e Mobile (Flutter) e evitando vazamento
 * de detalhes internos sensiveis (RNF02).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Intercepta qualquer excecao da hierarquia de negocio do Agiliza Delivery.
     */
    @ExceptionHandler(AgilizaException.class)
    public ResponseEntity<ApiErrorResponse> tratarAgilizaException(
            AgilizaException ex, HttpServletRequest request) {

        log.warn("Exceção de negócio capturada [{}]: {}", ex.getCodigoErro(), ex.getMessage());

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(ex.getStatusHttp().value())
                .error(ex.getStatusHttp().getReasonPhrase())
                .codigo(ex.getCodigoErro())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(ex.getStatusHttp()).body(body);
    }

    /**
     * Trata erros de validacao de DTOs disparados pelo @Valid do Bean Validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> tratarValidacaoCampos(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<CampoErro> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> new CampoErro(erro.getField(), erro.getDefaultMessage()))
                .toList();

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .codigo(CodigoErro.DADOS_INVALIDOS)
                .message("Um ou mais campos contêm valores inválidos. Verifique os dados e tente novamente.")
                .path(request.getRequestURI())
                .campos(campos)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Trata violacoes de constraints a nivel de entidade JPA.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> tratarConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<CampoErro> campos = ex.getConstraintViolations().stream()
                .map(cv -> new CampoErro(cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .codigo(CodigoErro.DADOS_INVALIDOS)
                .message("Restrições de validação de dados foram violadas.")
                .path(request.getRequestURI())
                .campos(campos)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Trata violacoes de integridade relacional do banco de dados (chaves unicas, chaves estrangeiras).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> tratarIntegridadeBanco(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.error("Violação de integridade de dados no banco: {}", ex.getMessage());

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .codigo(CodigoErro.DADO_DUPLICADO)
                .message("Conflito de integridade de dados: o registro viola uma restrição única ou relacional.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Trata corpo de requisicao malformado, JSON invalido ou valor de enum incorreto.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> tratarCorpoInvalido(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .codigo(CodigoErro.CORPO_REQUISICAO_INVALIDO)
                .message("O corpo da requisição é inválido, está malformado ou contém valores incompatíveis.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Trata parametros com tipos incompativeis na URL ou query params (ex: UUID mal formatado).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> tratarParametroInvalido(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String mensagem = String.format("O parâmetro '%s' recebeu o valor '%s', que é inválido para o tipo esperado.",
                ex.getName(), ex.getValue());

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .codigo(CodigoErro.PARAMETRO_INVALIDO)
                .message(mensagem)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Trata falha de autorizacao propagada pelo Spring Security.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> tratarAcessoNegadoSecurity(
            AccessDeniedException ex, HttpServletRequest request) {

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .codigo(CodigoErro.ACESSO_NEGADO)
                .message("Você não tem permissão para acessar este recurso.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Fallback para qualquer excecao inesperada ou nao mapeada.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> tratarErroInesperado(
            Exception ex, HttpServletRequest request) {

        log.error("Erro interno não tratado na aplicação em [{}]:", request.getRequestURI(), ex);

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .codigo(CodigoErro.ERRO_INTERNO_SERVIDOR)
                .message("Ocorreu um erro interno inesperado no servidor. Por favor, tente novamente mais tarde.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
