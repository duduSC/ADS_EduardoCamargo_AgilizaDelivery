package br.com.agilizadelivery.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.agilizadelivery.dto.request.AlterarDisponibilidadeMotoboyRequest;
import br.com.agilizadelivery.dto.request.AlterarStatusEntregaRequest;
import br.com.agilizadelivery.dto.response.AcertoDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.EntregaDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.LoteDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.MotoboyResponse;
import br.com.agilizadelivery.dto.response.ReciboDoEntregadorDetalheResponse;
import br.com.agilizadelivery.dto.response.ReciboDoEntregadorResponse;

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

    /** Recibos do proprio entregador, do mais recente para o mais antigo (D013). */
    List<ReciboDoEntregadorResponse> listarRecibos(UUID estabelecimentoId, UUID usuarioId);

    /** Recibo do proprio entregador com as entregas; de outro entregador, 404 (D013). */
    ReciboDoEntregadorDetalheResponse buscarRecibo(UUID estabelecimentoId, UUID usuarioId, UUID reciboId);
}
