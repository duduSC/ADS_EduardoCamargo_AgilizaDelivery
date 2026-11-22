package br.com.agilizadelivery.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.request.AlterarDisponibilidadeMotoboyRequest;
import br.com.agilizadelivery.dto.request.AlterarStatusEntregaRequest;
import br.com.agilizadelivery.dto.response.AcertoDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.EntregaDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.ExtratoAcertoResponse;
import br.com.agilizadelivery.dto.response.ItemDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.LoteDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.MotoboyResponse;
import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.RequisicaoInvalidaException;
import br.com.agilizadelivery.exception.handler.CodigoErro;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.service.EntregaService;
import br.com.agilizadelivery.service.EntregadorService;
import br.com.agilizadelivery.service.ExtratoAcertoService;
import br.com.agilizadelivery.service.LoteEntregaService;
import br.com.agilizadelivery.service.MotoboyService;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Fachada do aplicativo do entregador (D008). Resolve o entregador a partir
 * do usuario do token, confere a posse do lote ou da entrega e delega as
 * regras aos servicos de dominio (maquina de estados, repasse, contestacao).
 */
@Service
@Transactional(readOnly = true)
public class EntregadorServiceImpl implements EntregadorService {

    private final MotoboyRepository motoboyRepository;
    private final LoteEntregaRepository loteRepository;
    private final EntregaRepository entregaRepository;
    private final CaixaRepository caixaRepository;
    private final MotoboyService motoboyService;
    private final LoteEntregaService loteService;
    private final EntregaService entregaService;
    private final ExtratoAcertoService extratoService;
    private final BuscadorTenant buscador;

    public EntregadorServiceImpl(
            MotoboyRepository motoboyRepository,
            LoteEntregaRepository loteRepository,
            EntregaRepository entregaRepository,
            CaixaRepository caixaRepository,
            MotoboyService motoboyService,
            LoteEntregaService loteService,
            EntregaService entregaService,
            ExtratoAcertoService extratoService,
            BuscadorTenant buscador) {
        this.motoboyRepository = motoboyRepository;
        this.loteRepository = loteRepository;
        this.entregaRepository = entregaRepository;
        this.caixaRepository = caixaRepository;
        this.motoboyService = motoboyService;
        this.loteService = loteService;
        this.entregaService = entregaService;
        this.extratoService = extratoService;
        this.buscador = buscador;
    }

    @Override
    public UUID motoboyDoUsuario(UUID estabelecimentoId, UUID usuarioId) {
        return motoboy(estabelecimentoId, usuarioId).getId();
    }

    @Override
    public MotoboyResponse buscarProprio(UUID estabelecimentoId, UUID usuarioId) {
        return DtoMapper.toResponse(motoboy(estabelecimentoId, usuarioId));
    }

    /**
     * O entregador so alterna entre ONLINE e OFFLINE (DVP 1.4.4). EM_ROTA e
     * do ciclo do lote: pedi-lo e entrada invalida; sair de EM_ROTA por aqui
     * e recusado pelo MotoboyService como transicao invalida.
     */
    @Override
    @Transactional
    public MotoboyResponse alterarDisponibilidade(
            UUID estabelecimentoId, UUID usuarioId, AlterarDisponibilidadeMotoboyRequest request) {
        buscador.travarLoja(estabelecimentoId);
        if (request.status() == StatusDisponibilidadeMotoboy.EM_ROTA) {
            throw new RequisicaoInvalidaException(
                    "O entregador só pode informar ONLINE ou OFFLINE; EM_ROTA é definido pelo lote.");
        }
        return motoboyService.alterarDisponibilidade(
                estabelecimentoId, motoboyDoUsuario(estabelecimentoId, usuarioId), request);
    }

    @Override
    @Transactional
    public MotoboyResponse atualizarPosicao(
            UUID estabelecimentoId, UUID usuarioId, BigDecimal latitude, BigDecimal longitude) {
        return motoboyService.atualizarPosicao(
                estabelecimentoId, motoboyDoUsuario(estabelecimentoId, usuarioId), latitude, longitude);
    }

    /**
     * O lote em execucao (EM_ROTA) tem prioridade sobre o despachado. Pela
     * maquina de estados ha no maximo um lote aberto por entregador; se
     * houver mais de um, vale o despachado primeiro.
     */
    @Override
    public Optional<LoteDoEntregadorResponse> buscarLoteAtivo(UUID estabelecimentoId, UUID usuarioId) {
        UUID motoboyId = motoboyDoUsuario(estabelecimentoId, usuarioId);
        return primeiroLote(estabelecimentoId, motoboyId, StatusLote.EM_ROTA)
                .or(() -> primeiroLote(estabelecimentoId, motoboyId, StatusLote.DESPACHADO))
                .map(this::montarLote);
    }

    @Override
    @Transactional
    public LoteDoEntregadorResponse iniciarLote(UUID estabelecimentoId, UUID usuarioId, UUID loteId) {
        buscador.travarLoja(estabelecimentoId);
        UUID motoboyId = motoboyDoUsuario(estabelecimentoId, usuarioId);
        LoteEntrega lote = loteDoEntregador(estabelecimentoId, motoboyId, loteId);
        loteService.iniciarRota(estabelecimentoId, loteId, motoboyId);
        return montarLote(lote);
    }

    /**
     * ENTREGUE ou FALHA a partir de EM_ROTA (UC02, passo 5 e A1). A ultima
     * entrega finalizada encerra o lote e devolve o entregador a ONLINE
     * (EncerramentoLote, UC02 passo 6).
     */
    @Override
    @Transactional
    public EntregaDoEntregadorResponse alterarStatusEntrega(
            UUID estabelecimentoId, UUID usuarioId, UUID entregaId, AlterarStatusEntregaRequest request) {
        buscador.travarLoja(estabelecimentoId);
        UUID motoboyId = motoboyDoUsuario(estabelecimentoId, usuarioId);
        Entrega entrega = entregaDoEntregador(estabelecimentoId, motoboyId, entregaId);
        entregaService.alterarStatus(estabelecimentoId, entregaId, usuarioId, request);
        return montarEntrega(entrega);
    }

    /** Contestacao do valor (UC08 A1, E1 e E2, regras no EntregaService). */
    @Override
    @Transactional
    public EntregaDoEntregadorResponse contestar(UUID estabelecimentoId, UUID usuarioId, UUID entregaId) {
        buscador.travarLoja(estabelecimentoId);
        UUID motoboyId = motoboyDoUsuario(estabelecimentoId, usuarioId);
        Entrega entrega = entregaDoEntregador(estabelecimentoId, motoboyId, entregaId);
        entregaService.contestar(estabelecimentoId, entregaId, motoboyId);
        return montarEntrega(entrega);
    }

    /**
     * Acerto do proprio entregador (UC08) e a situacao do caixa do dia.
     *
     * PROVISORIO (T009): sem periodo informado, vale o dia corrente
     * (00:00 ate o fim do dia), que e o mesmo dia de referencia do caixa
     * usado pela PoliticaCorrecao. O periodo informado precisa caber em um
     * unico dia, para que o status do caixa devolvido seja inequivoco.
     */
    @Override
    public AcertoDoEntregadorResponse acerto(
            UUID estabelecimentoId, UUID usuarioId, LocalDateTime inicio, LocalDateTime fim) {
        if ((inicio == null) != (fim == null)) {
            throw new RequisicaoInvalidaException(
                    "Informe 'inicio' e 'fim' juntos, ou nenhum dos dois para o dia atual.",
                    CodigoErro.PARAMETRO_INVALIDO);
        }
        LocalDateTime de = inicio;
        LocalDateTime ate = fim;
        if (de == null) {
            LocalDate hoje = HorarioLocal.hoje();
            de = hoje.atStartOfDay();
            ate = hoje.atTime(LocalTime.MAX);
        }
        LocalDate dataReferencia = de.toLocalDate();
        if (!dataReferencia.equals(ate.toLocalDate())) {
            throw new RequisicaoInvalidaException(
                    "O período do acerto deve estar contido em um único dia (o dia do caixa).",
                    CodigoErro.PARAMETRO_INVALIDO);
        }

        UUID motoboyId = motoboyDoUsuario(estabelecimentoId, usuarioId);
        ExtratoAcertoResponse extrato = extratoService.gerarExtrato(estabelecimentoId, motoboyId, de, ate);
        Optional<Caixa> caixa = caixaRepository.findByEstabelecimentoIdAndDataReferencia(
                estabelecimentoId, dataReferencia);
        return new AcertoDoEntregadorResponse(
                extrato,
                dataReferencia,
                caixa.map(Caixa::getStatus).orElse(null),
                caixa.map(Caixa::estaAberto).orElse(false));
    }

    private Motoboy motoboy(UUID estabelecimentoId, UUID usuarioId) {
        return motoboyRepository.findByUsuarioId(usuarioId)
                .filter(m -> estabelecimentoId.equals(m.getUsuario().getEstabelecimento().getId()))
                .orElseThrow(() -> new EntidadeNaoEncontradaException(
                        "Não há cadastro de entregador para o usuário autenticado.",
                        CodigoErro.MOTOBOY_NAO_ENCONTRADO));
    }

    /** Lote de outro entregador responde como inexistente (HU13, HU14). */
    private LoteEntrega loteDoEntregador(UUID estabelecimentoId, UUID motoboyId, UUID loteId) {
        return loteRepository.findByIdAndEstabelecimentoId(loteId, estabelecimentoId)
                .filter(lote -> motoboyId.equals(lote.getMotoboy().getId()))
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(LoteEntrega.class, loteId));
    }

    /** Entrega fora dos lotes do entregador responde como inexistente. */
    private Entrega entregaDoEntregador(UUID estabelecimentoId, UUID motoboyId, UUID entregaId) {
        return entregaRepository.findByIdAndEstabelecimentoId(entregaId, estabelecimentoId)
                .filter(entrega -> entrega.getLote() != null
                        && motoboyId.equals(entrega.getLote().getMotoboy().getId()))
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Entrega.class, entregaId));
    }

    private Optional<LoteEntrega> primeiroLote(UUID estabelecimentoId, UUID motoboyId, StatusLote status) {
        return loteRepository.findByEstabelecimentoIdAndMotoboyIdAndStatus(estabelecimentoId, motoboyId, status)
                .stream()
                .min(Comparator.comparing(LoteEntrega::getDespachadoEm,
                        Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private LoteDoEntregadorResponse montarLote(LoteEntrega lote) {
        List<EntregaDoEntregadorResponse> entregas = entregaRepository
                .findByLoteIdOrderByOrdemRotaAsc(lote.getId()).stream()
                .map(EntregadorServiceImpl::montarEntrega)
                .toList();
        return new LoteDoEntregadorResponse(
                lote.getId(),
                lote.getStatus(),
                lote.getQtdEntregas(),
                lote.getDespachadoEm(),
                lote.getConcluidoEm(),
                lote.getEstabelecimento().getNomeFantasia(),
                DtoMapper.toDto(lote.getEstabelecimento().getCoordenada()),
                entregas);
    }

    private static EntregaDoEntregadorResponse montarEntrega(Entrega entrega) {
        List<ItemDoEntregadorResponse> itens = entrega.getPedidoExterno() == null
                ? List.of()
                : entrega.getPedidoExterno().getItens().stream()
                        .map(item -> new ItemDoEntregadorResponse(
                                item.getDescricao(), item.getQuantidade(), item.getObservacao()))
                        .toList();
        return new EntregaDoEntregadorResponse(
                entrega.getId(),
                entrega.getLote() == null ? null : entrega.getLote().getId(),
                entrega.getOrdemRota(),
                entrega.getStatus(),
                entrega.getNomeCliente(),
                entrega.getTelefoneCliente(),
                entrega.getEnderecoCompleto(),
                entrega.getBairro(),
                entrega.getCep(),
                entrega.getComplemento(),
                entrega.getPontoReferencia(),
                DtoMapper.toDto(entrega.getCoordenada()),
                entrega.getValorPedido(),
                entrega.getFormaPagamento(),
                entrega.getPagoOnline(),
                entrega.getTrocoPara(),
                entrega.getObservacoes(),
                entrega.getDistanciaKm(),
                entrega.getValorRepasse(),
                entrega.getMotivoFalha(),
                entrega.getFinalizadaEm(),
                entrega.getPendenteRevisao(),
                entrega.getMotivoRevisao(),
                itens);
    }
}
