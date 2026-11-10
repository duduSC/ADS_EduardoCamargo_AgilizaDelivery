package br.com.agilizadelivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.FalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.EtapaFalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.StatusFalhaIntegracao;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.repository.FalhaIntegracaoRepository;
import br.com.agilizadelivery.service.impl.FalhaIntegracaoServiceImpl;

@ExtendWith(MockitoExtension.class)
class FalhaIntegracaoServiceTest {

    @Mock
    private FalhaIntegracaoRepository repository;

    private FalhaIntegracaoService service;
    private UUID estabelecimentoId;
    private FalhaIntegracao falha;

    @BeforeEach
    void setUp() {
        service = new FalhaIntegracaoServiceImpl(repository);
        estabelecimentoId = UUID.randomUUID();
        ConfiguracaoIntegracao configuracao = new ConfiguracaoIntegracao();
        configuracao.setId(UUID.randomUUID());
        falha = new FalhaIntegracao();
        falha.setId(10L);
        falha.setConfiguracaoIntegracao(configuracao);
        falha.setEtapa(EtapaFalhaIntegracao.CONSULTA);
        falha.setMensagemErro("timeout");
        falha.setTentativas((short) 5);
        falha.setStatus(StatusFalhaIntegracao.PENDENTE);
    }

    private void falhaEncontrada() {
        when(repository.findByIdAndConfiguracaoIntegracaoEstabelecimentoId(10L, estabelecimentoId))
                .thenReturn(Optional.of(falha));
    }

    @Test
    @DisplayName("Deve marcar falha pendente como reprocessada")
    void deveReprocessar() {
        falhaEncontrada();
        when(repository.save(falha)).thenReturn(falha);

        assertEquals(StatusFalhaIntegracao.REPROCESSADA, service.marcarReprocessada(estabelecimentoId, 10L).status());
        assertNotNull(falha.getResolvidoEm());
    }

    @Test
    @DisplayName("Deve descartar falha pendente e recusar resolver falha já resolvida")
    void deveDescartarUmaUnicaVez() {
        falhaEncontrada();
        when(repository.save(falha)).thenReturn(falha);

        assertEquals(StatusFalhaIntegracao.DESCARTADA, service.descartar(estabelecimentoId, 10L).status());
        assertThrows(RegraDeNegocioException.class, () -> service.marcarReprocessada(estabelecimentoId, 10L));
    }

    @Test
    @DisplayName("Deve isolar falhas de outra loja e consultar o painel de alertas (RNF04)")
    void deveConsultar() {
        when(repository.findByConfiguracaoIntegracaoEstabelecimentoIdAndStatus(
                estabelecimentoId, StatusFalhaIntegracao.PENDENTE)).thenReturn(List.of(falha));
        when(repository.countByConfiguracaoIntegracaoEstabelecimentoIdAndStatus(
                estabelecimentoId, StatusFalhaIntegracao.PENDENTE)).thenReturn(1L);

        assertEquals(1, service.listarPorStatus(estabelecimentoId, StatusFalhaIntegracao.PENDENTE).size());
        assertEquals(1L, service.contarPorStatus(estabelecimentoId, StatusFalhaIntegracao.PENDENTE));
        assertThrows(EntidadeNaoEncontradaException.class, () -> service.descartar(UUID.randomUUID(), 10L));
    }
}
