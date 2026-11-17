package br.com.agilizadelivery.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Edicao de dados do entregador pelo ADMIN_RESTAURANTE (UC05, passo 2).
 * Apenas os campos operacionais que podem mudar; CPF e credencial
 * (email/senha) ficam de fora.
 */
public record EditarMotoboyRequest(
        @Size(max = 15) String telefone,
        @Size(max = 8) String placaVeiculo) {
}
