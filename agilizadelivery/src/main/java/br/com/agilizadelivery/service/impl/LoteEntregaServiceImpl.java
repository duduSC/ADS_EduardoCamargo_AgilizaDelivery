package br.com.agilizadelivery.service.impl;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.request.DespacharLoteRequest;
import br.com.agilizadelivery.dto.response.LoteEntregaResponse;
import br.com.agilizadelivery.dto.response.RotaLoteResponse;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.exception.AcessoNegadoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.EntregaInaptaParaLoteException;
import br.com.agilizadelivery.exception.LimiteLoteExcedidoException;
import br.com.agilizadelivery.exception.MotoboyIndisponivelException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.service.LoteEntregaService;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.service.support.EncerramentoLote;
import br.com.agilizadelivery.service.support.TransicaoEntrega;

/**
 * Despacho e execucao dos lotes (UC01, UC02).
 *
 * O agrupamento e manual (DVP 1.2.4): o operador escolhe as entregas e
 * a ordem das paradas, que o entregador segue. A ordem nao influencia o
 * repasse, apurado por entrega pela distancia loja -> cliente (1.5.5).
 */
@Service
@Transactional(readOnly = true)
public class LoteEntregaServiceImpl implements LoteEntregaService {

    private final LoteEntregaRepository repository;
    private final EntregaRepository entregaRepository;
    private final MotoboyRepository motoboyRepository;
    private final BuscadorTenant buscador;
    private final TransicaoEntrega transicao;

    public LoteEntregaServiceImpl(
            LoteEntregaRepository repository,
            EntregaRepository entregaRepository,
            MotoboyRepository motoboyRepository,
            BuscadorTenant buscador,
            TransicaoEntrega transicao) {
        this.repository = repository;
        this.entregaRepository = entregaRepository;
        this.motoboyRepository = motoboyRepository;
        this.buscador = buscador;
        this.transicao = transicao;
    }

    @Override
    @Transactional
    public LoteEntregaResponse despachar(
            UUID estabelecimentoId,
            UUID usuarioDespachanteId,
            DespacharLoteRequest request) {
        Estabelecimento estabelecimento = buscador.estabelecimentoAtivo(estabelecimentoId);
        Usuario despachante = buscador.usuarioComPerfil(
                estabelecimentoId, usuarioDespachanteId, PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE);

        List<UUID> ordem = request.entregaIds();
        exigirSemRepeticao(ordem);
        if (ordem.size() > estabelecimento.getMaxPedidosPorLote()) {
            throw new LimiteLoteExcedidoException(ordem.size(), estabelecimento.getMaxPedidosPorLote());
        }

        Motoboy motoboy = buscador.motoboy(estabelecimentoId, request.motoboyId());
        exigirDisponivel(motoboy);

        List<Entrega> entregas = carregarNaOrdem(estabelecimentoId, ordem);
        entregas.forEach(LoteEntregaServiceImpl::exigirApta);

        LocalDateTime agora = LocalDateTime.now();
        LoteEntrega lote = new LoteEntrega();
        lote.setEstabelecimento(estabelecimento);
        lote.setMotoboy(motoboy);
        lote.setUsuarioDespachante(despachante);
        lote.setStatus(StatusLote.DESPACHADO);
        lote.setQtdEntregas((short) entregas.size());
        lote.setValorTotalRepasse(EncerramentoLote.somarRepasse(entregas));
        lote.setDespachadoEm(agora);
        LoteEntrega salvo = repository.save(lote);

        for (int i = 0; i < entregas.size(); i++) {
            Entrega entrega = entregas.get(i);
            transicao.transitar(entrega, StatusEntrega.DESPACHADA, despachante, null, agora);
            entrega.setLote(salvo);
            entrega.setOrdemRota((short) (i + 1));
            entrega.setDespachadaEm(agora);
        }
        entregaRepository.saveAll(entregas);

        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.EM_ROTA);
        motoboyRepository.save(motoboy);
        return DtoMapper.toResponse(salvo);
    }

    @Override
    public LoteEntregaResponse buscarPorId(UUID estabelecimentoId, UUID loteId) {
        return DtoMapper.toResponse(buscar(estabelecimentoId, loteId));
    }

    @Override
    public List<LoteEntregaResponse> listarPorEstabelecimentoEStatus(UUID estabelecimentoId, StatusLote status) {
        return repository.findByEstabelecimentoIdAndStatus(estabelecimentoId, status).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    @Override
    public List<LoteEntregaResponse> listarPorMotoboy(UUID estabelecimentoId, UUID motoboyId, StatusLote status) {
        return repository.findByEstabelecimentoIdAndMotoboyIdAndStatus(estabelecimentoId, motoboyId, status).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    /** O entregador sai da loja (UC02, passo 4). */
    @Override
    @Transactional
    public LoteEntregaResponse iniciarRota(UUID estabelecimentoId, UUID loteId, UUID motoboyId) {
        LoteEntrega lote = buscar(estabelecimentoId, loteId);
        exigirDono(lote, motoboyId);
        if (lote.getStatus() != StatusLote.DESPACHADO) {
            throw new TransicaoEstadoInvalidaException("Lote", lote.getStatus(), StatusLote.EM_ROTA);
        }

        LocalDateTime agora = LocalDateTime.now();
        List<Entrega> entregas = entregaRepository.findByLoteIdOrderByOrdemRotaAsc(lote.getId()).stream()
                .filter(entrega -> entrega.getStatus() == StatusEntrega.DESPACHADA)
                .toList();
        entregas.forEach(entrega -> transicao.transitar(
                entrega, StatusEntrega.EM_ROTA, lote.getMotoboy().getUsuario(), null, agora));
        entregaRepository.saveAll(entregas);

        lote.setStatus(StatusLote.EM_ROTA);
        return DtoMapper.toResponse(repository.save(lote));
    }

    /** Devolucao do lote antes da saida (UC01 A4). */
    @Override
    @Transactional
    public LoteEntregaResponse devolverDespacho(UUID estabelecimentoId, UUID loteId, UUID usuarioId) {
        LoteEntrega lote = buscar(estabelecimentoId, loteId);
        Usuario autor = buscador.usuarioComPerfil(
                estabelecimentoId, usuarioId, PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE);
        if (lote.getStatus() != StatusLote.DESPACHADO) {
            throw new TransicaoEstadoInvalidaException("Lote", lote.getStatus(), StatusLote.CANCELADO);
        }

        LocalDateTime agora = LocalDateTime.now();
        List<Entrega> devolvidas = entregaRepository.findByLoteIdOrderByOrdemRotaAsc(lote.getId()).stream()
                .filter(entrega -> entrega.getStatus() == StatusEntrega.DESPACHADA)
                .toList();
        for (Entrega entrega : devolvidas) {
            transicao.transitar(entrega, StatusEntrega.AGUARDANDO_DESPACHO, autor, null, agora);
            entrega.setLote(null);
            entrega.setOrdemRota(null);
            entrega.setDespachadaEm(null);
        }
        entregaRepository.saveAll(devolvidas);

        lote.setStatus(StatusLote.CANCELADO);
        LoteEntrega salvo = repository.save(lote);

        Motoboy motoboy = lote.getMotoboy();
        if (motoboy.getStatusDisponibilidade() == StatusDisponibilidadeMotoboy.EM_ROTA) {
            motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.ONLINE);
            motoboyRepository.save(motoboy);
        }
        return DtoMapper.toResponse(salvo);
    }

    @Override
    public RotaLoteResponse buscarRota(UUID estabelecimentoId, UUID loteId) {
        LoteEntrega lote = buscar(estabelecimentoId, loteId);
        return new RotaLoteResponse(
                lote.getId(),
                DtoMapper.toDto(lote.getEstabelecimento().getCoordenada()),
                entregaRepository.findByLoteIdOrderByOrdemRotaAsc(lote.getId()).stream()
                        .map(DtoMapper::toParada)
                        .toList());
    }

    private LoteEntrega buscar(UUID estabelecimentoId, UUID loteId) {
        return repository.findByIdAndEstabelecimentoId(loteId, estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(LoteEntrega.class, loteId));
    }

    private List<Entrega> carregarNaOrdem(UUID estabelecimentoId, List<UUID> ordem) {
        Map<UUID, Entrega> porId = entregaRepository.findByEstabelecimentoIdAndIdIn(estabelecimentoId, ordem)
                .stream()
                .collect(Collectors.toMap(Entrega::getId, Function.identity()));
        return ordem.stream()
                .map(id -> {
                    Entrega entrega = porId.get(id);
                    if (entrega == null) {
                        throw EntidadeNaoEncontradaException.porId(Entrega.class, id);
                    }
                    return entrega;
                })
                .toList();
    }

    /** Entregador ATIVO e ONLINE (HU02, UC01 E1). */
    private static void exigirDisponivel(Motoboy motoboy) {
        String nome = motoboy.getUsuario().getNome();
        if (motoboy.getStatusCadastro() != StatusCadastroMotoboy.ATIVO) {
            throw new MotoboyIndisponivelException(nome, "o cadastro está bloqueado");
        }
        if (!motoboy.podeReceberLote()) {
            throw new MotoboyIndisponivelException(nome, "ele não está ONLINE");
        }
    }

    private static void exigirApta(Entrega entrega) {
        if (entrega.getStatus() != StatusEntrega.AGUARDANDO_DESPACHO) {
            throw new EntregaInaptaParaLoteException(
                    entrega.getId(), "ela está com status " + entrega.getStatus());
        }
        if (Boolean.TRUE.equals(entrega.getPendenteRevisao())) {
            throw new EntregaInaptaParaLoteException(
                    entrega.getId(), "ela está pendente de revisão (" + entrega.getMotivoRevisao() + ")");
        }
    }

    private static void exigirDono(LoteEntrega lote, UUID motoboyId) {
        if (!lote.getMotoboy().getId().equals(motoboyId)) {
            throw new AcessoNegadoException("O lote não pertence a este entregador.");
        }
    }

    private static void exigirSemRepeticao(List<UUID> ids) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new RegraDeNegocioException("A mesma entrega foi informada mais de uma vez na rota.");
        }
    }
}
