package br.com.agilizadelivery.exception.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Detalhe de erro de validacao de campo de formulario ou payload JSON (Bean Validation).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CampoErro {

    private String campo;
    private String mensagem;
}
