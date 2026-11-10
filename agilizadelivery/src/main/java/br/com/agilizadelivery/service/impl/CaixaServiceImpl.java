package br.com.agilizadelivery.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.request.AbrirCaixaRequest;
import br.com.agilizadelivery.dto.response.CaixaResponse;
import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.exception.CaixaJaAbertoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.repository.ReciboRepository;
import br.com.agilizadelivery.service.CaixaService;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(readOnly = true)
@Slf4j
public class CaixaServiceImpl implements CaixaService {

    private final CaixaRepository caixaRepository;
    private final ReciboRepository reciboRepository;
    private final BuscadorTenant buscador;

    public CaixaServiceImpl(
            CaixaRepository caixaRepository,
            ReciboRepository reciboRepository,
            BuscadorTenant buscador) {
        this.caixaRepository = caixaRepository;
        this.reciboRepository = reciboRepository;
        this.buscador = buscador;
    }

    @Override
    @Transactional
    public CaixaResponse abrirCaixa(UUID estabelecimentoId, AbrirCaixaRequest request) {
        LocalDate dataReferencia = request.dataReferencia();
        log.info("Tentativa de abertura de caixa para estabelecimento '{}' na data '{}'",
                estabelecimentoId, dataReferencia);

        Estabelecimento estabelecimento = buscador.estabelecimentoAtivo(estabelecimentoId);
        if (caixaRepository.existsByEstabelecimentoIdAndDataReferencia(estabelecimentoId, dataReferencia)) {
            throw new CaixaJaAbertoException(dataReferencia);
        }

        Caixa caixa = new Caixa();
        caixa.setEstabelecimento(estabelecimento);
        caixa.setDataReferencia(dataReferencia);
        caixa.setAbertoEm(LocalDateTime.now());
        caixa.setStatus(StatusCaixa.ABERTO);
        Caixa salvo = caixaRepository.save(caixa);
        log.info("Caixa '{}' aberto com sucesso para o estabelecimento '{}'", salvo.getId(), estabelecimentoId);
        return DtoMapper.toResponse(salvo);
    }

    @Override
    public CaixaResponse buscarPorId(UUID estabelecimentoId, UUID caixaId) {
        return DtoMapper.toResponse(buscar(estabelecimentoId, caixaId));
    }

    @Override
    public CaixaResponse buscarPorEstabelecimentoEData(UUID estabelecimentoId, LocalDate dataReferencia) {
        return caixaRepository.findByEstabelecimentoIdAndDataReferencia(estabelecimentoId, dataReferencia)
                .map(DtoMapper::toResponse)
                .orElseThrow(() -> new EntidadeNaoEncontradaException(
                        String.format("Nenhum caixa encontrado para o estabelecimento '%s' na data %s.",
                                estabelecimentoId, dataReferencia)));
    }

    @Override
    public List<CaixaResponse> listarPorEstabelecimentoEStatus(UUID estabelecimentoId, StatusCaixa status) {
        return caixaRepository.findByEstabelecimentoIdAndStatus(estabelecimentoId, status).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    /** Fecha o expediente e grava os consolidados (UC03, passo 7). */
    @Override
    @Transactional
    public CaixaResponse fecharCaixa(UUID estabelecimentoId, UUID caixaId, UUID usuarioFechamentoId) {
        log.info("Fechando caixa '{}' pelo usuário '{}'", caixaId, usuarioFechamentoId);

        Caixa caixa = buscar(estabelecimentoId, caixaId);
        if (!caixa.estaAberto()) {
            throw new CaixaFechadoException("fechar o caixa");
        }
        Usuario usuarioFechamento = buscador.usuarioComPerfil(
                estabelecimentoId, usuarioFechamentoId, PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE);

        List<Recibo> validos = reciboRepository.findByEstabelecimentoIdAndCaixaId(estabelecimentoId, caixaId)
                .stream()
                .filter(recibo -> !StatusRecibo.CANCELADO.equals(recibo.getStatus()))
                .toList();

        caixa.setQtdRecibos((short) validos.size());
        caixa.setValorTotal(validos.stream()
                .map(Recibo::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        caixa.setStatus(StatusCaixa.FECHADO);
        caixa.setFechadoEm(LocalDateTime.now());
        caixa.setUsuarioFechamento(usuarioFechamento);
        Caixa fechado = caixaRepository.save(caixa);
        log.info("Caixa '{}' fechado com sucesso", caixaId);
        return DtoMapper.toResponse(fechado);
    }

    /** Reabertura, privativa do Administrador do Restaurante (UC03 A3). */
    @Override
    @Transactional
    public CaixaResponse reabrirCaixa(
            UUID estabelecimentoId,
            UUID caixaId,
            UUID usuarioReaberturaId,
            String motivoReabertura) {
        log.info("Solicitação de reabertura do caixa '{}' pelo usuário '{}'", caixaId, usuarioReaberturaId);

        Caixa caixa = buscar(estabelecimentoId, caixaId);
        if (!caixa.podeSerReaberto()) {
            throw new TransicaoEstadoInvalidaException("Caixa", caixa.getStatus(), StatusCaixa.REABERTO);
        }
        if (motivoReabertura == null || motivoReabertura.isBlank()) {
            throw new RegraDeNegocioException("É obrigatório informar a justificativa para a reabertura do caixa.");
        }
        Usuario usuarioReabertura = buscador.usuarioComPerfil(
                estabelecimentoId, usuarioReaberturaId, PerfilUsuario.ADMIN_RESTAURANTE);

        caixa.setStatus(StatusCaixa.REABERTO);
        caixa.setReabertoEm(LocalDateTime.now());
        caixa.setUsuarioReabertura(usuarioReabertura);
        caixa.setMotivoReabertura(motivoReabertura.trim());
        Caixa reaberto = caixaRepository.save(caixa);
        log.info("Caixa '{}' reaberto com sucesso", caixaId);
        return DtoMapper.toResponse(reaberto);
    }

    private Caixa buscar(UUID estabelecimentoId, UUID caixaId) {
        return caixaRepository.findByIdAndEstabelecimentoId(caixaId, estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Caixa.class, caixaId));
    }
}
