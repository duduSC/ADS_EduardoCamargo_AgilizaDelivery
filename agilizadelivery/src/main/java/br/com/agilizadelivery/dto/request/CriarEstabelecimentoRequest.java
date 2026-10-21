package br.com.agilizadelivery.dto.request;

import java.math.BigDecimal;

import br.com.agilizadelivery.dto.CoordenadaDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CNPJ;

public record CriarEstabelecimentoRequest(
        @NotBlank @Size(max = 150) String razaoSocial,
        @NotBlank @Size(max = 100) String nomeFantasia,
        @NotBlank @CNPJ String cnpj,
        @Size(max = 15) String telefone,
        @NotBlank @Size(max = 255) String enderecoCompleto,
        @NotNull @Valid CoordenadaDto coordenada,
        @NotNull @Positive Short maxPedidosPorLote,
        @PositiveOrZero BigDecimal valorRepasseForaDeArea) {
}
