package br.com.agilizadelivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;

public record EstabelecimentoResponse(
        UUID id,
        String razaoSocial,
        String nomeFantasia,
        String cnpj,
        String telefone,
        String enderecoCompleto,
        CoordenadaDto coordenada,
        StatusEstabelecimento status,
        Short maxPedidosPorLote,
        BigDecimal valorRepasseForaDeArea,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {
}
