package br.com.agilizadelivery.service;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.dto.response.ExtratoAcertoResponse;

/**
 * Extrato do turno: previa do acerto para o operador (UC03) e
 * acompanhamento do ganho pelo entregador (UC08).
 */
public interface ExtratoAcertoService {

    ExtratoAcertoResponse gerarExtrato(
            UUID estabelecimentoId,
            UUID motoboyId,
            LocalDateTime inicio,
            LocalDateTime fim);
}
