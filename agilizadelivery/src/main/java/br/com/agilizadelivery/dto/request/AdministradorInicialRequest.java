package br.com.agilizadelivery.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Usuario master da loja, criado junto com o estabelecimento (UC06, HU11). */
public record AdministradorInicialRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 150) String loginEmail,
        @NotBlank @Size(min = 8, max = 72) String senha) {
}
