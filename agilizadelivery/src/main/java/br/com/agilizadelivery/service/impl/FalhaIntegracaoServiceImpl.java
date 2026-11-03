package br.com.agilizadelivery.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.response.FalhaIntegracaoResponse;
import br.com.agilizadelivery.entity.model.FalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.StatusFalhaIntegracao;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.repository.FalhaIntegracaoRepository;
import br.com.agilizadelivery.service.FalhaIntegracaoService;

@Service
@Transactional(readOnly = true)
public class FalhaIntegracaoServiceImpl implements FalhaIntegracaoService {

    private final FalhaIntegracaoRepository repository;

    public FalhaIntegracaoServiceImpl(FalhaIntegracaoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<FalhaIntegracaoResponse> listarPorStatus(
            UUID estabelecimentoId,
            StatusFalhaIntegracao status) {
        return repository.findByConfiguracaoIntegracaoEstabelecimentoIdAndStatus(estabelecimentoId, status)
                .stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    @Override
    public long contarPorStatus(UUID estabelecimentoId, StatusFalhaIntegracao status) {
        return repository.countByConfiguracaoIntegracaoEstabelecimentoIdAndStatus(estabelecimentoId, status);
    }

    @Override
    @Transactional
    public FalhaIntegracaoResponse marcarReprocessada(UUID estabelecimentoId, Long falhaId) {
        FalhaIntegracao falha = buscarPorEstabelecimento(estabelecimentoId, falhaId);
        validarPendente(falha);
        falha.setStatus(StatusFalhaIntegracao.REPROCESSADA);
        falha.setResolvidoEm(LocalDateTime.now());
        return DtoMapper.toResponse(repository.save(falha));
    }

    @Override
    @Transactional
    public FalhaIntegracaoResponse descartar(UUID estabelecimentoId, Long falhaId) {
        FalhaIntegracao falha = buscarPorEstabelecimento(estabelecimentoId, falhaId);
        validarPendente(falha);
        falha.setStatus(StatusFalhaIntegracao.DESCARTADA);
        falha.setResolvidoEm(LocalDateTime.now());
        return DtoMapper.toResponse(repository.save(falha));
    }

    private FalhaIntegracao buscarPorEstabelecimento(UUID estabelecimentoId, Long falhaId) {
        return repository.findByIdAndConfiguracaoIntegracaoEstabelecimentoId(falhaId, estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(FalhaIntegracao.class, falhaId));
    }

    private void validarPendente(FalhaIntegracao falha) {
        if (falha.getStatus() != StatusFalhaIntegracao.PENDENTE) {
            throw new RegraDeNegocioException(
                    "Somente falhas pendentes podem ser reprocessadas ou descartadas.");
        }
    }
}
