package br.com.agilizadelivery.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;

public record MotoboyResponse(
        UUID id,
        UUID usuarioId,
        String nome,
        String telefone,
        String placaVeiculo,
        StatusCadastroMotoboy statusCadastro,
        StatusDisponibilidadeMotoboy statusDisponibilidade,
        CoordenadaDto ultimaPosicao,
        LocalDateTime ultimaPosicaoEm,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {
}
