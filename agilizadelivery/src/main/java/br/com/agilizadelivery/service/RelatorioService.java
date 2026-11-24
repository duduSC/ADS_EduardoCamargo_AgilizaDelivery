package br.com.agilizadelivery.service;

import java.time.LocalDate;
import java.util.UUID;

import br.com.agilizadelivery.dto.response.RelatorioDoDiaResponse;

/** Relatorio simples do dia (D013, item 5). */
public interface RelatorioService {

    RelatorioDoDiaResponse doDia(UUID estabelecimentoId, LocalDate data);
}
