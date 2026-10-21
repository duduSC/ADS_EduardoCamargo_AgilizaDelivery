package br.com.agilizadelivery.dto.request;

import java.math.BigDecimal;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.entity.model.enums.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CriarEntregaAvulsaRequest(
        @NotBlank @Size(max = 120) String nomeCliente,
        @Size(max = 20) String telefoneCliente,
        @NotBlank @Size(max = 255) String enderecoCompleto,
        @Size(max = 100) String bairro,
        @Size(max = 9) String cep,
        @Size(max = 100) String complemento,
        @Size(max = 150) String pontoReferencia,
        @Valid CoordenadaDto coordenada,
        @PositiveOrZero BigDecimal valorPedido,
        FormaPagamento formaPagamento,
        Boolean pagoOnline,
        @PositiveOrZero BigDecimal trocoPara,
        String observacoes) {
}
