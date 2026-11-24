package br.com.agilizadelivery.service;

import java.util.List;

import java.util.UUID;

import br.com.agilizadelivery.dto.request.SalvarConfiguracaoIntegracaoRequest;
import br.com.agilizadelivery.dto.response.ConfiguracaoIntegracaoResponse;

public interface ConfiguracaoIntegracaoService {

    ConfiguracaoIntegracaoResponse salvar(
            UUID estabelecimentoId,
            SalvarConfiguracaoIntegracaoRequest request);

    ConfiguracaoIntegracaoResponse buscarPorEstabelecimentoEProvedor(
            UUID estabelecimentoId,
            String provedor);

    List<ConfiguracaoIntegracaoResponse> listarPorEstabelecimento(UUID estabelecimentoId);

    ConfiguracaoIntegracaoResponse alterarAtivo(UUID estabelecimentoId, UUID configuracaoId, boolean ativo);
}
