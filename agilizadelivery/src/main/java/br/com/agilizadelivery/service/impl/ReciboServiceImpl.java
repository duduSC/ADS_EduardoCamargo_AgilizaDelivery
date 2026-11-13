package br.com.agilizadelivery.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.request.GerarReciboRequest;
import br.com.agilizadelivery.dto.response.ReciboResponse;
import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.EntregaInaptaParaReciboException;
import br.com.agilizadelivery.exception.ReciboInalteravelException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.ReciboRepository;
import br.com.agilizadelivery.service.ReciboService;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.service.support.EncerramentoLote;
import lombok.extern.slf4j.Slf4j;

/**
 * Acerto financeiro com o entregador (UC03, HU07).
 *
 * O recibo vincula as entregas do periodo e pertence ao caixa do dia.
 * Enquanto o caixa estiver aberto, um recibo, mesmo pago, pode ser
 * cancelado; isso desvincula as entregas e as libera para ajuste e novo
 * acerto (UC03 A2).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ReciboServiceImpl implements ReciboService {

    private final ReciboRepository repository;
    private final EntregaRepository entregaRepository;
    private final CaixaRepository caixaRepository;
    private final BuscadorTenant buscador;

    public ReciboServiceImpl(
            ReciboRepository repository,
            EntregaRepository entregaRepository,
            CaixaRepository caixaRepository,
            BuscadorTenant buscador) {
        this.repository = repository;
        this.entregaRepository = entregaRepository;
        this.caixaRepository = caixaRepository;
        this.buscador = buscador;
    }

    @Override
    @Transactional
    public ReciboResponse gerar(UUID estabelecimentoId, UUID usuarioGeradorId, GerarReciboRequest request) {
        if (!request.periodoFim().isAfter(request.periodoInicio())) {
            throw new RegraDeNegocioException("O fim do período deve ser posterior ao início.");
        }
        Usuario gerador = buscador.usuarioComPerfil(
                estabelecimentoId, usuarioGeradorId, PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE);
        Motoboy motoboy = buscador.motoboy(estabelecimentoId, request.motoboyId());
        Caixa caixa = caixaAbertoDoDia(estabelecimentoId, request.periodoInicio().toLocalDate());

        List<Entrega> aptas = entregaRepository
                .findByEstabelecimentoIdAndLoteMotoboyIdAndFinalizadaEmBetweenOrderByFinalizadaEmAsc(
                        estabelecimentoId, motoboy.getId(), request.periodoInicio(), request.periodoFim())
                .stream()
                .filter(Entrega::podeEntrarEmRecibo)
                .toList();
        if (aptas.isEmpty()) {
            throw new RegraDeNegocioException(
                    "Não há entregas finalizadas, sem revisão pendente e fora de recibo no período informado.");
        }
        aptas.stream()
                .filter(entrega -> entrega.getValorRepasse() == null)
                .findFirst()
                .ifPresent(entrega -> {
                    throw new EntregaInaptaParaReciboException(entrega.getId(),
                            "o valor de repasse não está definido; configure o valor de fora de área da loja");
                });

        Recibo recibo = new Recibo();
        recibo.setEstabelecimento(caixa.getEstabelecimento());
        recibo.setCaixa(caixa);
        recibo.setMotoboy(motoboy);
        recibo.setUsuarioGerador(gerador);
        recibo.setDataReferencia(caixa.getDataReferencia());
        recibo.setPeriodoInicio(request.periodoInicio());
        recibo.setPeriodoFim(request.periodoFim());
        recibo.setQtdEntregas((short) aptas.size());
        recibo.setValorTotal(EncerramentoLote.somarRepasse(aptas));
        recibo.setStatus(StatusRecibo.GERADO);
        Recibo salvo = repository.save(recibo);

        aptas.forEach(entrega -> entrega.setRecibo(salvo));
        entregaRepository.saveAll(aptas);
        return DtoMapper.toResponse(salvo);
    }

    @Override
    public ReciboResponse buscarPorId(UUID estabelecimentoId, UUID reciboId) {
        return DtoMapper.toResponse(buscar(estabelecimentoId, reciboId));
    }

    @Override
    public List<ReciboResponse> listarPorCaixa(UUID estabelecimentoId, UUID caixaId) {
        return paraResposta(repository.findByEstabelecimentoIdAndCaixaId(estabelecimentoId, caixaId));
    }

    @Override
    public List<ReciboResponse> listarPorEntregadorEStatus(
            UUID estabelecimentoId,
            UUID motoboyId,
            StatusRecibo status) {
        return paraResposta(repository.findByEstabelecimentoIdAndMotoboyIdAndStatus(estabelecimentoId, motoboyId, status));
    }

    @Override
    public List<ReciboResponse> listarPorDataReferencia(UUID estabelecimentoId, LocalDate dataReferencia) {
        return paraResposta(repository.findByEstabelecimentoIdAndDataReferencia(estabelecimentoId, dataReferencia));
    }

    /** PIX realizado fora da plataforma (UC03, passo 6). */
    @Override
    @Transactional
    public ReciboResponse marcarComoPago(UUID estabelecimentoId, UUID reciboId, UUID usuarioId) {
        Recibo recibo = buscar(estabelecimentoId, reciboId);
        buscador.usuarioComPerfil(estabelecimentoId, usuarioId, PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE);
        if (recibo.getStatus() != StatusRecibo.GERADO) {
            throw new TransicaoEstadoInvalidaException("Recibo", recibo.getStatus(), StatusRecibo.PAGO);
        }
        if (!recibo.permiteAlteracao()) {
            throw new CaixaFechadoException("marcar o recibo como pago");
        }
        recibo.setStatus(StatusRecibo.PAGO);
        recibo.setPagoEm(LocalDateTime.now());
        return DtoMapper.toResponse(repository.save(recibo));
    }

    /** Cancelamento com o caixa aberto, inclusive de recibo pago (UC03 A2). */
    @Override
    @Transactional
    public ReciboResponse cancelar(UUID estabelecimentoId, UUID reciboId, UUID usuarioId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new RegraDeNegocioException("O motivo do cancelamento do recibo é obrigatório.");
        }
        Recibo recibo = buscar(estabelecimentoId, reciboId);
        buscador.usuarioComPerfil(estabelecimentoId, usuarioId, PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE);
        if (recibo.getStatus() == StatusRecibo.CANCELADO) {
            throw new ReciboInalteravelException("ele já está cancelado");
        }
        if (!recibo.podeSerCancelado()) {
            throw new ReciboInalteravelException("o caixa do período já está fechado");
        }

        List<Entrega> entregas = entregaRepository.findByReciboId(recibo.getId());
        entregas.forEach(entrega -> entrega.setRecibo(null));
        entregaRepository.saveAll(entregas);

        recibo.setStatus(StatusRecibo.CANCELADO);
        log.info("Recibo '{}' cancelado pelo usuário '{}'. Motivo: {}", reciboId, usuarioId, motivo.trim());
        return DtoMapper.toResponse(repository.save(recibo));
    }

    @Override
    public List<ReciboResponse> listarPorMotoboy(UUID estabelecimentoId, UUID motoboyId) {
        return paraResposta(repository.findByEstabelecimentoIdAndMotoboyId(estabelecimentoId, motoboyId));
    }

    private Caixa caixaAbertoDoDia(UUID estabelecimentoId, LocalDate data) {
        Caixa caixa = caixaRepository.findByEstabelecimentoIdAndDataReferencia(estabelecimentoId, data)
                .orElseThrow(() -> new EntidadeNaoEncontradaException(String.format(
                        "Nenhum caixa encontrado para o estabelecimento '%s' na data %s.", estabelecimentoId, data)));
        if (!caixa.estaAberto()) {
            throw new CaixaFechadoException("gerar recibo de acerto");
        }
        return caixa;
    }

    private Recibo buscar(UUID estabelecimentoId, UUID reciboId) {
        return repository.findByIdAndEstabelecimentoId(reciboId, estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Recibo.class, reciboId));
    }

    private static List<ReciboResponse> paraResposta(List<Recibo> recibos) {
        return recibos.stream().map(DtoMapper::toResponse).toList();
    }
}
