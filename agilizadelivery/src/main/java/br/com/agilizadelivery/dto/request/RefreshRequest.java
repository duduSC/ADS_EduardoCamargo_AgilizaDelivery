package br.com.agilizadelivery.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Renovacao da sessao (PROVISORIO: refresh token fora do DVP 3.1). */
public record RefreshRequest(@NotBlank String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=***]";
    }
}
