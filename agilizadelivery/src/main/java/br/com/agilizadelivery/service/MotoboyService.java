package br.com.agilizadelivery.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.request.AlterarDisponibilidadeMotoboyRequest;
import br.com.agilizadelivery.dto.request.CriarMotoboyRequest;
import br.com.agilizadelivery.dto.response.MotoboyResponse;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;

public interface MotoboyService {

    MotoboyResponse criar(UUID estabelecimentoId, CriarMotoboyRequest request);

    MotoboyResponse buscarPorId(UUID estabelecimentoId, UUID motoboyId);

    List<MotoboyResponse> listarPorEstabelecimento(UUID estabelecimentoId);

    List<MotoboyResponse> listarPorStatusCadastro(UUID estabelecimentoId, StatusCadastroMotoboy status);

    MotoboyResponse alterarStatusCadastro(
            UUID estabelecimentoId,
            UUID motoboyId,
            StatusCadastroMotoboy status);

    MotoboyResponse alterarDisponibilidade(
            UUID estabelecimentoId,
            UUID motoboyId,
            AlterarDisponibilidadeMotoboyRequest request);

    MotoboyResponse atualizarPosicao(
            UUID estabelecimentoId,
            UUID motoboyId,
            BigDecimal latitude,
            BigDecimal longitude);
}
