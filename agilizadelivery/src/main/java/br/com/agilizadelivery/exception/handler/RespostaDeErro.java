package br.com.agilizadelivery.exception.handler;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import br.com.agilizadelivery.exception.TokenInvalidoException;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Fabrica unica do ApiErrorResponse, usada pelo handler, pela pagina de
 * erro, pelos filtros e pelo container (Constituicao 6.4).
 *
 * A resposta e sempre JSON, mesmo que o Accept nao o aceite: senao o erro
 * viraria outro erro, em HTML ou sem corpo.
 *
 * PROVISORIO (T014): 405, 406, 413 e 415 reaproveitam os codigos
 * existentes; o catalogo nao tem codigo proprio para eles nem para o 503.
 */
public final class RespostaDeErro {

    static final String MENSAGEM_INTERNA =
            "Ocorreu um erro interno inesperado no servidor. Por favor, tente novamente mais tarde.";
    static final String MENSAGEM_INDISPONIVEL =
            "O serviço está temporariamente indisponível. Tente novamente em instantes.";

    private RespostaDeErro() {
    }

    public static ApiErrorResponse corpo(HttpStatusCode status, CodigoErro codigo, String mensagem, String caminho) {
        return corpo(status, codigo, mensagem, caminho, null);
    }

    static ApiErrorResponse corpo(HttpStatusCode status, CodigoErro codigo, String mensagem, String caminho,
            List<CampoErro> campos) {
        // o enum recebido manda na descricao: 422 ja sai como "Unprocessable Entity" no contrato
        HttpStatus conhecido = status instanceof HttpStatus exato ? exato : HttpStatus.resolve(status.value());
        return ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(conhecido == null ? "Error" : conhecido.getReasonPhrase())
                .codigo(codigo)
                .message(mensagem)
                .path(Objects.requireNonNullElse(caminho, ""))
                .campos(campos)
                .build();
    }

    /** Corpo padrao para um status que nao tem causa mais especifica. */
    public static ApiErrorResponse corpoPadrao(HttpStatusCode status, String caminho) {
        return corpo(status, codigoPara(status.value()), mensagemPara(status.value()), caminho);
    }

    static ResponseEntity<ApiErrorResponse> entidade(ApiErrorResponse corpo) {
        return ResponseEntity.status(corpo.getStatus()).contentType(MediaType.APPLICATION_JSON).body(corpo);
    }

    /** Para quem responde antes do DispatcherServlet (filtros). */
    public static void escrever(ObjectMapper json, HttpServletResponse response, ApiErrorResponse corpo)
            throws IOException {
        response.setStatus(corpo.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), corpo);
    }

    static CodigoErro codigoPara(int status) {
        if (status >= 500) {
            return CodigoErro.ERRO_INTERNO_SERVIDOR;
        }
        return switch (status) {
            case 401 -> CodigoErro.NAO_AUTENTICADO;
            case 403 -> CodigoErro.ACESSO_NEGADO;
            case 404, 405 -> CodigoErro.RECURSO_NAO_ENCONTRADO;
            case 409 -> CodigoErro.DADO_DUPLICADO;
            case 413, 415 -> CodigoErro.CORPO_REQUISICAO_INVALIDO;
            default -> CodigoErro.PARAMETRO_INVALIDO;
        };
    }

    static String mensagemPara(int status) {
        if (status == 503) {
            return MENSAGEM_INDISPONIVEL;
        }
        if (status >= 500) {
            return MENSAGEM_INTERNA;
        }
        return switch (status) {
            case 400 -> "A requisição é inválida. Confira o caminho e os parâmetros.";
            case 401 -> TokenInvalidoException.MENSAGEM;
            case 403 -> "Você não tem permissão para acessar este recurso.";
            case 404 -> "O recurso solicitado não existe.";
            case 405 -> "O método HTTP usado não é aceito neste recurso.";
            case 406 -> "A API responde apenas em JSON (application/json).";
            case 413 -> "O corpo da requisição é maior que o limite permitido.";
            case 415 -> "O tipo do corpo da requisição não é aceito; envie application/json.";
            default -> "A requisição não pôde ser processada.";
        };
    }
}
