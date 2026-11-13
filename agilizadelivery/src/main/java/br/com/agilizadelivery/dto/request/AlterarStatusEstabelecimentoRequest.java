package br.com.agilizadelivery.dto.request;

import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import jakarta.validation.constraints.NotNull;

public record AlterarStatusEstabelecimentoRequest(
        @NotNull StatusEstabelecimento status) {
}
