package br.com.agilizadelivery.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String loginEmail,
        @NotBlank String senha) {
}
