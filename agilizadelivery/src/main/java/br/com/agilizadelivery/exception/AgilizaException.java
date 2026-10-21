package br.com.agilizadelivery.exception;

import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.exception.handler.CodigoErro;
import lombok.Getter;

/**
 * Superclasse abstrata de todas as excecoes de negocio da aplicacao Agiliza Delivery.
 *
 * Herda de RuntimeException para manter os servicos desacoplados de checagens forcadas
 * e integrados ao modelo transacional declarativo do Spring (@Transactional).
 *
 * Encapsula o status HTTP sugerido e o codigo catalogado de erro para consumo das APIs.
 */
@Getter
public abstract class AgilizaException extends RuntimeException {

    private final HttpStatus statusHttp;
    private final CodigoErro codigoErro;

    protected AgilizaException(String mensagem, HttpStatus statusHttp, CodigoErro codigoErro) {
        super(mensagem);
        this.statusHttp = statusHttp;
        this.codigoErro = codigoErro;
    }

    protected AgilizaException(String mensagem, Throwable causa, HttpStatus statusHttp, CodigoErro codigoErro) {
        super(mensagem, causa);
        this.statusHttp = statusHttp;
        this.codigoErro = codigoErro;
    }
}
