package br.com.agilizadelivery.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.request.SalvarConfiguracaoIntegracaoRequest;
import br.com.agilizadelivery.dto.response.ConfiguracaoIntegracaoResponse;
import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.enums.Provedor;
import br.com.agilizadelivery.exception.ConflitoDadosException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.service.ConfiguracaoIntegracaoService;

@Service
@Transactional(readOnly = true)
public class ConfiguracaoIntegracaoServiceImpl implements ConfiguracaoIntegracaoService {

    private final ConfiguracaoIntegracaoRepository repository;
    private final EstabelecimentoRepository estabelecimentoRepository;

    public ConfiguracaoIntegracaoServiceImpl(
            ConfiguracaoIntegracaoRepository repository,
            EstabelecimentoRepository estabelecimentoRepository) {
        this.repository = repository;
        this.estabelecimentoRepository = estabelecimentoRepository;
    }

    @Override
    @Transactional
    public ConfiguracaoIntegracaoResponse salvar(
            UUID estabelecimentoId,
            SalvarConfiguracaoIntegracaoRequest request) {
        Estabelecimento estabelecimento = estabelecimentoRepository.findById(estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Estabelecimento.class, estabelecimentoId));

        ConfiguracaoIntegracao configuracao = repository
                .findByEstabelecimentoIdAndProvedor(estabelecimentoId, request.provedor())
                .orElseGet(() -> {
                    ConfiguracaoIntegracao nova = new ConfiguracaoIntegracao();
                    nova.setEstabelecimento(estabelecimento);
                    nova.setProvedor(request.provedor());
                    nova.setAtivo(true);
                    return nova;
                });

        repository.findByProvedorAndMerchantId(request.provedor(), request.merchantId())
                .filter(outra -> !outra.getId().equals(configuracao.getId()))
                .ifPresent(outra -> {
                    throw new ConflitoDadosException(
                            "O identificador merchant informado já está vinculado a outra configuração.");
                });

        configuracao.setMerchantId(request.merchantId());
        return DtoMapper.toResponse(repository.save(configuracao));
    }

    @Override
    public ConfiguracaoIntegracaoResponse buscarPorEstabelecimentoEProvedor(
            UUID estabelecimentoId,
            Provedor provedor) {
        ConfiguracaoIntegracao configuracao = repository
                .findByEstabelecimentoIdAndProvedor(estabelecimentoId, provedor)
                .orElseThrow(() -> new EntidadeNaoEncontradaException(
                        String.format(
                                "Nenhuma configuração de integração encontrada para o estabelecimento '%s' e provedor '%s'.",
                                estabelecimentoId,
                                provedor)));
        return DtoMapper.toResponse(configuracao);
    }

    @Override
    public List<ConfiguracaoIntegracaoResponse> listarPorEstabelecimento(UUID estabelecimentoId) {
        return repository.findByEstabelecimentoId(estabelecimentoId).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ConfiguracaoIntegracaoResponse alterarAtivo(
            UUID estabelecimentoId,
            UUID configuracaoId,
            boolean ativo) {
        ConfiguracaoIntegracao configuracao = repository
                .findByIdAndEstabelecimentoId(configuracaoId, estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(ConfiguracaoIntegracao.class, configuracaoId));

        configuracao.setAtivo(ativo);
        return DtoMapper.toResponse(repository.save(configuracao));
    }
}
