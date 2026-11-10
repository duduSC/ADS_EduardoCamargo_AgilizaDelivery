package br.com.agilizadelivery.service;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.dto.request.SalvarDistanciaPrecoRequest;
import br.com.agilizadelivery.dto.response.DistanciaPrecoResponse;
import br.com.agilizadelivery.entity.model.DistanciaPreco;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.FaixaPrecoInvalidaException;
import br.com.agilizadelivery.repository.DistanciaPrecoRepository;
import br.com.agilizadelivery.service.impl.DistanciaPrecoServiceImpl;
import br.com.agilizadelivery.service.support.BuscadorTenant;

@ExtendWith(MockitoExtension.class)
class DistanciaPrecoServiceTest {

    private static final LocalDate VIGENCIA = LocalDate.of(2026, 11, 10);

    @Mock
    private DistanciaPrecoRepository repository;

    @Mock
    private BuscadorTenant buscador;

    private DistanciaPrecoService service;
    private Estabelecimento estabelecimento;

    @BeforeEach
    void setUp() {
        service = new DistanciaPrecoServiceImpl(repository, buscador);
        estabelecimento = Fixtures.estabelecimento();
    }

    private static SalvarDistanciaPrecoRequest faixa(String de, String ate, String valor) {
        return new SalvarDistanciaPrecoRequest(reais(de), reais(ate), reais(valor), VIGENCIA);
    }

    @Test
    @DisplayName("Deve substituir a tabela inativando as faixas anteriores, sem excluí-las (HU10)")
    void deveSubstituirTabela() {
        DistanciaPreco antiga = Fixtures.faixa(estabelecimento, "0", "3", "9.00");
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        when(repository.findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(estabelecimento.getId()))
                .thenReturn(List.of(antiga));
        when(repository.saveAllAndFlush(anyList())).then(returnsFirstArg());

        List<DistanciaPrecoResponse> novas = service.substituirTabela(estabelecimento.getId(),
                List.of(faixa("3", "5", "12.00"), faixa("0", "3", "6.00")));

        assertFalse(antiga.getAtivo());
        verify(repository).saveAll(List.of(antiga));
        assertEquals(2, novas.size());
        assertEquals(reais("0"), novas.getFirst().deKm());
        assertEquals(reais("6.00"), novas.getFirst().valorPago());
    }

    @Test
    @DisplayName("Deve recusar tabela vazia, com sobreposição, lacuna ou sem começar em zero (UC05 E1)")
    void deveRecusarTabelaInvalida() {
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        List<List<SalvarDistanciaPrecoRequest>> invalidas = List.of(
                List.of(),
                List.of(faixa("0", "3", "9.00"), faixa("2", "5", "12.00")),
                List.of(faixa("0", "3", "9.00"), faixa("4", "5", "12.00")),
                List.of(faixa("1", "3", "9.00")),
                List.of(faixa("0", "3", "0")),
                List.of(faixa("0", "0", "9.00")));

        invalidas.forEach(tabela -> assertThrows(FaixaPrecoInvalidaException.class, () ->
                service.substituirTabela(estabelecimento.getId(), tabela)));
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("Deve encontrar a faixa da distância e recusar distância negativa")
    void deveBuscarFaixaParaDistancia() {
        when(repository.findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(estabelecimento.getId())).thenReturn(List.of(
                Fixtures.faixa(estabelecimento, "0", "3", "9.00"),
                Fixtures.faixa(estabelecimento, "3", "5", "12.00")));

        assertEquals(reais("12.00"),
                service.buscarFaixaParaDistancia(estabelecimento.getId(), reais("4.7")).valorPago());
        assertThrows(EntidadeNaoEncontradaException.class, () ->
                service.buscarFaixaParaDistancia(estabelecimento.getId(), reais("6")));
        assertThrows(FaixaPrecoInvalidaException.class, () ->
                service.buscarFaixaParaDistancia(estabelecimento.getId(), reais("-1")));
    }

    @Test
    @DisplayName("Deve listar faixas ativas e o histórico")
    void deveListar() {
        DistanciaPreco faixa = Fixtures.faixa(estabelecimento, "0", "3", "9.00");
        when(repository.findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(estabelecimento.getId()))
                .thenReturn(List.of(faixa));
        when(repository.findByEstabelecimentoIdOrderByVigenteDesdeDescDeKmAsc(estabelecimento.getId()))
                .thenReturn(List.of(faixa, faixa));

        assertEquals(1, service.listarFaixasAtivas(estabelecimento.getId()).size());
        assertEquals(2, service.listarHistorico(estabelecimento.getId()).size());
    }
}
