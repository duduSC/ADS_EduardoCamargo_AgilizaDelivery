package br.com.agilizadelivery.dto.request;

import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import jakarta.validation.constraints.NotNull;

public record AlterarDisponibilidadeMotoboyRequest(
        @NotNull StatusDisponibilidadeMotoboy status) {
}
