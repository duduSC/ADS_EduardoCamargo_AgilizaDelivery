package br.com.agilizadelivery.service.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
import br.com.agilizadelivery.exception.AjusteValorNaoPermitidoException;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.service.Fixtures;

@ExtendWith(MockitoExtension.class)
class PoliticaCorrecaoTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 9);

    @Mock
    private CaixaRepository caixaRepository;

    private PoliticaCorrecao politica;
    private Estabelecimento estabelecimento;
    private Entrega entrega;

    @BeforeEach
    void setUp() {
        politica = new PoliticaCorrecao(caixaRepository);
        estabelecimento = Fixtures.estabelecimento();
        entrega = Fixtures.entrega(estabelecimento, StatusEntrega.ENTREGUE);
        entrega.setFinalizadaEm(DIA.atTime(21, 30));
    }

    private void caixaDoDia(StatusCaixa status) {
        Caixa caixa = Fixtures.caixa(estabelecimento, status, DIA);
        when(caixaRepository.findByEstabelecimentoIdAndDataReferencia(estabelecimento.getId(), DIA))
                .thenReturn(Optional.of(caixa));
    }

    @ParameterizedTest
    @EnumSource(value = StatusCaixa.class, names = {"ABERTO", "REABERTO"})
    @DisplayName("Deve permitir ajuste de entrega sem recibo com o caixa do dia aberto ou reaberto")
    void devePermitirAjusteComCaixaAberto(StatusCaixa status) {
        caixaDoDia(status);

        assertDoesNotThrow(() -> politica.exigirAjustePermitido(entrega));
    }

    @ParameterizedTest
    @EnumSource(value = StatusRecibo.class, names = {"GERADO", "PAGO"})
    @DisplayName("Deve recusar ajuste de entrega vinculada a recibo, mesmo com o caixa aberto (DVP 2.9)")
    void deveRecusarAjusteDeEntregaEmRecibo(StatusRecibo status) {
        Caixa aberto = Fixtures.caixa(estabelecimento, StatusCaixa.ABERTO, DIA);
        entrega.setRecibo(Fixtures.recibo(estabelecimento, aberto, Fixtures.motoboy(estabelecimento), status));

        assertThrows(AjusteValorNaoPermitidoException.class, () -> politica.exigirAjustePermitido(entrega));
    }

    @Test
    @DisplayName("Deve recusar ajuste com o caixa do dia fechado")
    void deveRecusarAjusteComCaixaFechado() {
        caixaDoDia(StatusCaixa.FECHADO);

        assertThrows(CaixaFechadoException.class, () -> politica.exigirAjustePermitido(entrega));
    }

    @Test
    @DisplayName("Deve recusar ajuste quando não existe caixa para o dia da entrega")
    void deveRecusarAjusteSemCaixa() {
        when(caixaRepository.findByEstabelecimentoIdAndDataReferencia(estabelecimento.getId(), DIA))
                .thenReturn(Optional.empty());

        assertThrows(AjusteValorNaoPermitidoException.class, () -> politica.exigirAjustePermitido(entrega));
    }

    @Test
    @DisplayName("Deve usar o caixa do dia corrente para entrega ainda não finalizada")
    void deveUsarCaixaDeHojeParaEntregaEmAndamento() {
        entrega.setFinalizadaEm(null);
        LocalDate hoje = LocalDateTime.now().toLocalDate();
        when(caixaRepository.findByEstabelecimentoIdAndDataReferencia(estabelecimento.getId(), hoje))
                .thenReturn(Optional.of(Fixtures.caixa(estabelecimento, StatusCaixa.ABERTO, hoje)));

        assertTrue(politica.caixaDoDiaAberto(entrega));
    }

    @Test
    @DisplayName("Deve recusar contestação com o caixa do dia fechado (UC08 E2)")
    void deveRecusarContestacaoComCaixaFechado() {
        caixaDoDia(StatusCaixa.FECHADO);

        assertFalse(politica.caixaDoDiaAberto(entrega));
        assertThrows(CaixaFechadoException.class, () -> politica.exigirCaixaDoDiaAberto(entrega, "contestar"));
    }
}
