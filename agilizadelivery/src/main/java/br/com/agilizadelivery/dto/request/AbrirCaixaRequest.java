package br.com.agilizadelivery.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record AbrirCaixaRequest(
        @NotNull LocalDate dataReferencia) {
}
