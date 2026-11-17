package br.com.agilizadelivery.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import br.com.agilizadelivery.dto.request.AlterarDisponibilidadeMotoboyRequest;
import br.com.agilizadelivery.dto.request.AlterarStatusEntregaRequest;
import br.com.agilizadelivery.dto.response.AcertoDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.EntregaDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.LoteDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.MotoboyResponse;

/**
 * Operacoes do aplicativo do entregador (UC02, UC08, D008).
 *
 * O entregador e sempre o do usuario autenticado: nenhum metodo recebe
 * motoboyId do cliente. Lote ou entrega de outro entregador e tratado como
 * inexistente, com a mesma resposta de um identificador que nao existe.
 */
public interface EntregadorService {

    /** Id do cadastro de entregador do usuario autenticado. */
    UUID motoboyDoUsuario(UUID estabelecimentoId, UUID usuarioId);

    MotoboyResponse buscarProprio(UUID estabelecimentoId, UUID usuarioId);

    MotoboyResponse alterarDisponibilidade(
            UUID estabelecimentoId, UUID usuarioId, AlterarDisponibilidadeMotoboyRequest request);

    MotoboyResponse atualizarPosicao(
            UUID estabelecimentoId, UUID usuarioId, BigDecimal latitude, BigDecimal longitude);

    Optional<LoteDoEntregadorResponse> buscarLoteAtivo(UUID estabelecimentoId, UUID usuarioId);

    LoteDoEntregadorResponse iniciarLote(UUID estabelecimentoId, UUID usuarioId, UUID loteId);

    EntregaDoEntregadorResponse alterarStatusEntrega(
            UUID estabelecimentoId, UUID usuarioId, UUID entregaId, AlterarStatusEntregaRequest request);

    EntregaDoEntregadorResponse contestar(UUID estabelecimentoId, UUID usuarioId, UUID entregaId);

    AcertoDoEntregadorResponse acerto(
            UUID estabelecimentoId, UUID usuarioId, LocalDateTime inicio, LocalDateTime fim);
}
