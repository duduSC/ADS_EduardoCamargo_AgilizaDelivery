package br.com.agilizadelivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.dto.request.SalvarConfiguracaoIntegracaoRequest;
import br.com.agilizadelivery.dto.response.ConfiguracaoIntegracaoResponse;
import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.enums.Provedor;
import br.com.agilizadelivery.exception.ConflitoDadosException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import br.com.agilizadelivery.service.impl.ConfiguracaoIntegracaoServiceImpl;
import br.com.agilizadelivery.service.support.BuscadorTenant;

@ExtendWith(MockitoExtension.class)
class ConfiguracaoIntegracaoServiceTest {

    @Mock
    private ConfiguracaoIntegracaoRepository repository;

    @Mock
    private BuscadorTenant buscador;

    private ConfiguracaoIntegracaoService service;
    private Estabelecimento estabelecimento;

    @BeforeEach
    void setUp() {
        service = new ConfiguracaoIntegracaoServiceImpl(repository, buscador);
        estabelecimento = Fixtures.estabelecimento();
    }

    private ConfiguracaoIntegracao configuracao(Estabelecimento dono, String merchantId) {
        ConfiguracaoIntegracao configuracao = new ConfiguracaoIntegracao();
        configuracao.setId(UUID.randomUUID());
        configuracao.setEstabelecimento(dono);
        configuracao.setProvedor(Provedor.IFOOD);
        configuracao.setMerchantId(merchantId);
        configuracao.setAtivo(true);
        return configuracao;
    }

    @Test
    @DisplayName("Deve vincular a loja ao provedor pelo merchant_id (UC05 passo 5)")
    void deveCriarVinculo() {
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        when(repository.findByEstabelecimentoIdAndProvedor(estabelecimento.getId(), Provedor.IFOOD))
                .thenReturn(Optional.empty());
        when(repository.findByProvedorAndMerchantId(Provedor.IFOOD, "merchant-1")).thenReturn(Optional.empty());
        when(repository.save(any(ConfiguracaoIntegracao.class))).then(returnsFirstArg());

        ConfiguracaoIntegracaoResponse salva = service.salvar(estabelecimento.getId(),
                new SalvarConfiguracaoIntegracaoRequest(Provedor.IFOOD, "merchant-1"));

        assertEquals("merchant-1", salva.merchantId());
        assertTrue(salva.ativo());
    }

    @Test
    @DisplayName("Deve atualizar o merchant_id da configuração existente")
    void deveAtualizarVinculo() {
        ConfiguracaoIntegracao existente = configuracao(estabelecimento, "antigo");
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        when(repository.findByEstabelecimentoIdAndProvedor(estabelecimento.getId(), Provedor.IFOOD))
                .thenReturn(Optional.of(existente));
        when(repository.findByProvedorAndMerchantId(Provedor.IFOOD, "novo")).thenReturn(Optional.empty());
        when(repository.save(existente)).thenReturn(existente);

        assertEquals("novo", service.salvar(estabelecimento.getId(),
                new SalvarConfiguracaoIntegracaoRequest(Provedor.IFOOD, "novo")).merchantId());
    }

    @Test
    @DisplayName("Deve recusar merchant_id já vinculado a outra loja")
    void deveRecusarMerchantDeOutraLoja() {
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        when(repository.findByEstabelecimentoIdAndProvedor(estabelecimento.getId(), Provedor.IFOOD))
                .thenReturn(Optional.empty());
        when(repository.findByProvedorAndMerchantId(Provedor.IFOOD, "merchant-1"))
                .thenReturn(Optional.of(configuracao(Fixtures.estabelecimento(), "merchant-1")));

        assertThrows(ConflitoDadosException.class, () -> service.salvar(estabelecimento.getId(),
                new SalvarConfiguracaoIntegracaoRequest(Provedor.IFOOD, "merchant-1")));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve ligar, desligar e consultar a integração da loja")
    void deveAlterarAtivoEConsultar() {
        ConfiguracaoIntegracao existente = configuracao(estabelecimento, "merchant-1");
        when(repository.findByIdAndEstabelecimentoId(existente.getId(), estabelecimento.getId()))
                .thenReturn(Optional.of(existente));
        when(repository.save(existente)).thenReturn(existente);
        when(repository.findByEstabelecimentoIdAndProvedor(estabelecimento.getId(), Provedor.IFOOD))
                .thenReturn(Optional.of(existente));
        when(repository.findByEstabelecimentoId(estabelecimento.getId())).thenReturn(List.of(existente));

        assertFalse(service.alterarAtivo(estabelecimento.getId(), existente.getId(), false).ativo());
        assertEquals(existente.getId(),
                service.buscarPorEstabelecimentoEProvedor(estabelecimento.getId(), Provedor.IFOOD).id());
        assertEquals(1, service.listarPorEstabelecimento(estabelecimento.getId()).size());
        assertThrows(EntidadeNaoEncontradaException.class, () ->
                service.buscarPorEstabelecimentoEProvedor(estabelecimento.getId(), Provedor.SIMULADOR));
    }
}
