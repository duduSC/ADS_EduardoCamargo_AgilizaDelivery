package br.com.agilizadelivery.exception.handler;

import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.com.agilizadelivery.exception.AgilizaException;
import br.com.agilizadelivery.exception.CorpoGrandeDemaisException;
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
 *
 * Log (Constituicao 6.1 a 6.4): erro inesperado e falha de infraestrutura
 * saem uma vez, com stacktrace; 4xx, sem stacktrace e sem os valores da
 * requisicao. O que falha antes do DispatcherServlet e do ControladorDeErro.
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

        return ResponseEntity.status(ex.getStatusHttp())
                .contentType(MediaType.APPLICATION_JSON)
                .body(RespostaDeErro.corpo(
                        ex.getStatusHttp(), ex.getCodigoErro(), ex.getMessage(), request.getRequestURI()));
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

        return camposInvalidos(campos, request);
    }

    /** @Valid em corpo que e lista (PUT /api/faixas-preco): campo no formato [0].valorPago. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> tratarValidacaoDeLista(
            HandlerMethodValidationException ex, HttpServletRequest request) {

        List<CampoErro> campos = ex.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream().map(erro -> {
                    String posicao = resultado.getContainerIndex() == null
                            ? "" : "[" + resultado.getContainerIndex() + "].";
                    String campo = erro instanceof FieldError doCampo
                            ? posicao + doCampo.getField()
                            : Objects.requireNonNullElse(resultado.getMethodParameter().getParameterName(), "valor");
                    return new CampoErro(campo, erro.getDefaultMessage());
                }))
                .toList();

        return camposInvalidos(campos, request);
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

        return RespostaDeErro.entidade(RespostaDeErro.corpo(HttpStatus.BAD_REQUEST, CodigoErro.DADOS_INVALIDOS,
                "Restrições de validação de dados foram violadas.", request.getRequestURI(), campos));
    }

    /**
     * Trata violacoes de integridade do banco: o codigo da checagem equivalente
     * do servico, quando a constraint e conhecida. A mensagem da excecao nao e
     * logada: traz os valores da linha.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> tratarIntegridadeBanco(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.CONFLICT;
        CodigoErro codigo = FalhaDeBanco.codigoDaConstraint(ex).orElse(CodigoErro.DADO_DUPLICADO);
        String mensagem = mensagemDoConflito(codigo);
        if (FalhaDeBanco.dadoInvalido(ex)) {
            status = HttpStatus.BAD_REQUEST;
            codigo = CodigoErro.DADOS_INVALIDOS;
            mensagem = "Um valor informado está fora do tamanho ou da faixa permitida.";
        }
        log.warn("Escrita recusada pelo banco [{}], SQLState {}",
                codigo, FalhaDeBanco.sqlState(ex).orElse("desconhecido"));

        return RespostaDeErro.entidade(RespostaDeErro.corpo(status, codigo, mensagem, request.getRequestURI()));
    }

    /**
     * Trata corpo de requisicao malformado, JSON invalido ou valor de enum incorreto.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> tratarCorpoInvalido(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        if (FalhaDeBanco.causa(ex, CorpoGrandeDemaisException.class).isPresent()) {
            return RespostaDeErro.entidade(
                    RespostaDeErro.corpoPadrao(HttpStatus.CONTENT_TOO_LARGE, request.getRequestURI()));
        }
        return RespostaDeErro.entidade(RespostaDeErro.corpo(HttpStatus.BAD_REQUEST,
                CodigoErro.CORPO_REQUISICAO_INVALIDO,
                "O corpo da requisição é inválido, está malformado ou contém valores incompatíveis.",
                request.getRequestURI()));
    }

    /**
     * Trata parametros obrigatorios ausentes na query string.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> tratarParametroAusente(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String mensagem = String.format("O parâmetro obrigatório '%s' não foi informado.", ex.getParameterName());

        return parametroInvalido(mensagem, request);
    }

    /** Variavel de caminho vazia ou em branco, como em /api/entregas/%20. */
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiErrorResponse> tratarVariavelDeCaminhoVazia(
            MissingPathVariableException ex, HttpServletRequest request) {

        if (!ex.isMissingAfterConversion()) {
            return tratarErroInesperado(ex, request);
        }
        return parametroInvalido(
                String.format("O parâmetro '%s' do caminho está vazio.", ex.getVariableName()), request);
    }

    /**
     * Trata parametros com tipos incompativeis na URL ou query params (ex: UUID mal formatado).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> tratarParametroInvalido(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String mensagem = String.format("O parâmetro '%s' recebeu o valor '%s', que é inválido para o tipo esperado.",
                ex.getName(), ex.getValue());

        return parametroInvalido(mensagem, request);
    }

    /**
     * Trata falha de autorizacao propagada pelo Spring Security.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> tratarAcessoNegadoSecurity(
            AccessDeniedException ex, HttpServletRequest request) {

        return RespostaDeErro.entidade(RespostaDeErro.corpoPadrao(HttpStatus.FORBIDDEN, request.getRequestURI()));
    }

    /**
     * Fallback. Antes de concluir que e inesperado, reconhece o que chega
     * embrulhado do commit ou da infraestrutura e os erros do proprio Spring
     * MVC (404, 405, 406, 415...), que trazem o status consigo.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> tratarErroInesperado(
            Exception ex, HttpServletRequest request) {

        String caminho = request.getRequestURI();

        var validacao = FalhaDeBanco.causa(ex, ConstraintViolationException.class);
        if (validacao.isPresent()) {
            return tratarConstraintViolation(validacao.get(), request);
        }
        var integridade = FalhaDeBanco.causa(ex, DataIntegrityViolationException.class);
        if (integridade.isPresent()) {
            return tratarIntegridadeBanco(integridade.get(), request);
        }
        if (FalhaDeBanco.concorrencia(ex)) {
            log.warn("Conflito de concorrência em [{}]: {}", caminho, ex.getClass().getSimpleName());
            return RespostaDeErro.entidade(RespostaDeErro.corpo(HttpStatus.CONFLICT, CodigoErro.DADO_DUPLICADO,
                    "Outra operação alterou este registro ao mesmo tempo. Atualize e tente novamente.", caminho));
        }
        if (FalhaDeBanco.indisponivel(ex)) {
            log.error("Banco de dados indisponível em [{}]:", caminho, ex);
            return RespostaDeErro.entidade(RespostaDeErro.corpoPadrao(HttpStatus.SERVICE_UNAVAILABLE, caminho));
        }
        if (ex instanceof ErrorResponse doSpring && doSpring.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(doSpring.getStatusCode())
                    .headers(cabecalhos -> cabecalhos.putAll(doSpring.getHeaders())) // Allow do 405
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(RespostaDeErro.corpoPadrao(doSpring.getStatusCode(), caminho));
        }

        log.error("Erro interno não tratado na aplicação em [{}]:", caminho, ex);

        return RespostaDeErro.entidade(RespostaDeErro.corpo(HttpStatus.INTERNAL_SERVER_ERROR,
                CodigoErro.ERRO_INTERNO_SERVIDOR, RespostaDeErro.MENSAGEM_INTERNA, caminho));
    }

    private static ResponseEntity<ApiErrorResponse> camposInvalidos(List<CampoErro> campos,
            HttpServletRequest request) {
        return RespostaDeErro.entidade(RespostaDeErro.corpo(HttpStatus.BAD_REQUEST, CodigoErro.DADOS_INVALIDOS,
                "Um ou mais campos contêm valores inválidos. Verifique os dados e tente novamente.",
                request.getRequestURI(), campos));
    }

    private static ResponseEntity<ApiErrorResponse> parametroInvalido(String mensagem, HttpServletRequest request) {
        return RespostaDeErro.entidade(RespostaDeErro.corpo(HttpStatus.BAD_REQUEST, CodigoErro.PARAMETRO_INVALIDO,
                mensagem, request.getRequestURI()));
    }

    private static String mensagemDoConflito(CodigoErro codigo) {
        return switch (codigo) {
            case EMAIL_JA_CADASTRADO -> "O e-mail informado já está cadastrado para outro usuário.";
            case CNPJ_JA_CADASTRADO -> "Já existe um estabelecimento cadastrado com o CNPJ informado.";
            case CPF_JA_CADASTRADO -> "Já existe um entregador cadastrado no sistema com o CPF informado.";
            case CAIXA_JA_ABERTO -> "Já existe um caixa registrado para este estabelecimento nesta data.";
            case PEDIDO_JA_IMPORTADO -> "Este pedido do provedor já foi importado.";
            default -> "Conflito de integridade de dados: o registro viola uma restrição única ou relacional.";
        };
    }
}
