package br.com.agilizadelivery.exception.handler;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

/**
 * Estrutura padronizada de resposta de erro HTTP (inspirada na RFC 7807 - Problem Details).
 *
 * Garante contrato uniforme para todos os endpoints consumidos pelo SPA React e App Flutter.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    /** Timestamp UTC do momento em que a falha ocorreu. */
    private Instant timestamp;

    /** Codigo de status HTTP (ex: 400, 404, 409, 422). */
    private Integer status;

    /** Descricao semantica do status HTTP (ex: "Not Found", "Unprocessable Entity"). */
    private String error;

    /** Codigo catalogado de erro para o frontend/mobile tomar decisoes em tela. */
    private CodigoErro codigo;

    /** Mensagem amigavel e descritiva para apresentacao ou log. */
    private String message;

    /** URI do endpoint que originou a requisicao. */
    private String path;

    /** Lista de campos com erro quando houver violacao de validacao (@Valid). */
    private List<CampoErro> campos;
}
