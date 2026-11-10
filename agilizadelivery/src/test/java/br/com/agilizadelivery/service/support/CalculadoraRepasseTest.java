package br.com.agilizadelivery.service.support;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.entity.model.DistanciaPreco;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.repository.DistanciaPrecoRepository;
import br.com.agilizadelivery.service.Fixtures;

@ExtendWith(MockitoExtension.class)
class CalculadoraRepasseTest {

    @Mock
    private DistanciaPrecoRepository distanciaPrecoRepository;

    private CalculadoraRepasse calculadora;
    private Estabelecimento estabelecimento;
    private DistanciaPreco ateTres;
    private DistanciaPreco ateCinco;

    @BeforeEach
    void setUp() {
        calculadora = new CalculadoraRepasse(distanciaPrecoRepository);
        estabelecimento = Fixtures.estabelecimento();
        ateTres = Fixtures.faixa(estabelecimento, "0", "3", "9.00");
        ateCinco = Fixtures.faixa(estabelecimento, "3", "5", "12.00");
    }

    private Entrega entregaCom(String distanciaKm) {
        Entrega entrega = Fixtures.entrega(estabelecimento, StatusEntrega.EM_ROTA);
        entrega.setDistanciaKm(distanciaKm == null ? null : reais(distanciaKm));
        entrega.setValorRepasse(null);
        return entrega;
    }

    @Test
    @DisplayName("Deve reproduzir o exemplo da HU06: 2,1 km + 4,7 km + 1,4 km = R$ 30,00")
    void deveCalcularOExemploDaHu06() {
        when(distanciaPrecoRepository.findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(estabelecimento.getId()))
                .thenReturn(List.of(ateTres, ateCinco));
        Entrega a = entregaCom("2.1");
        Entrega b = entregaCom("4.7");
        Entrega c = entregaCom("1.4");

        List.of(a, b, c).forEach(calculadora::aplicarFaixaVigente);

        assertEquals(reais("9.00"), a.getValorRepasse());
        assertEquals(reais("12.00"), b.getValorRepasse());
        assertEquals(reais("9.00"), c.getValorRepasse());
        assertSame(ateCinco, b.getFaixaPreco());
        assertEquals(0, reais("30.00").compareTo(EncerramentoLote.somarRepasse(List.of(a, b, c))));
    }

    @Test
    @DisplayName("Deve tratar o limite superior da faixa como exclusivo")
    void deveUsarIntervaloFechadoNoInicioEAbertoNoFim() {
        when(distanciaPrecoRepository.findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(estabelecimento.getId()))
                .thenReturn(List.of(ateTres, ateCinco));
        Entrega entrega = entregaCom("3");

        calculadora.aplicarFaixaVigente(entrega);

        assertSame(ateCinco, entrega.getFaixaPreco());
    }

    @Test
    @DisplayName("Deve aplicar o valor de fora de área, sem faixa e sem revisão, acima da última faixa")
    void deveAplicarValorForaDeArea() {
        when(distanciaPrecoRepository.findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(estabelecimento.getId()))
                .thenReturn(List.of(ateTres, ateCinco));
        Entrega entrega = entregaCom("6");

        calculadora.aplicarFaixaVigente(entrega);

        assertNull(entrega.getFaixaPreco());
        assertEquals(reais("15.00"), entrega.getValorRepasse());
        assertEquals(Boolean.FALSE, entrega.getPendenteRevisao());
    }

    @Test
    @DisplayName("Não deve alterar a entrega quando a distância é desconhecida")
    void naoDeveAlterarSemDistancia() {
        Entrega entrega = entregaCom(null);

        calculadora.aplicarFaixaVigente(entrega);

        assertNull(entrega.getValorRepasse());
        verifyNoInteractions(distanciaPrecoRepository);
    }

    @Test
    @DisplayName("Deve zerar o repasse de entrega cancelada antes da saída")
    void deveZerarRepasse() {
        Entrega entrega = entregaCom("2");
        entrega.setFaixaPreco(ateTres);

        calculadora.zerarRepasse(entrega);

        assertNull(entrega.getFaixaPreco());
        assertEquals(BigDecimal.ZERO, entrega.getValorRepasse());
    }
}
