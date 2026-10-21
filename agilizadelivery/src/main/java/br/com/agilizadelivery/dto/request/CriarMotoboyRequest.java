package br.com.agilizadelivery.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

public record CriarMotoboyRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 150) String loginEmail,
        @NotBlank @Size(min = 8, max = 72) String senha,
        @NotBlank @CPF String cpf,
        @NotBlank @Size(max = 15) String telefone,
        @Size(max = 8) String placaVeiculo) {
}
