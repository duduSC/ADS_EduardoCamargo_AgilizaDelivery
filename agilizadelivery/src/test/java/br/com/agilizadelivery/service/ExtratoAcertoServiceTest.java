package br.com.agilizadelivery.service;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.dto.response.ExtratoAcertoResponse;
import br.com.agilizadelivery.dto.response.LinhaExtratoResponse;
import br.com.agilizadelivery.dto.response.SituacaoAcerto;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.service.impl.ExtratoAcertoServiceImpl;
import br.com.agilizadelivery.service.support.BuscadorTenant;

@ExtendWith(MockitoExtension.class)
class ExtratoAcertoServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 9);
    private static final LocalDateTime INICIO = DIA.atTime(18, 0);
    private static final LocalDateTime FIM = DIA.atTime(23, 59);

    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private BuscadorTenant buscador;

    private ExtratoAcertoService service;
    private Estabelecimento estabelecimento;
    private Motoboy motoboy;
    private LoteEntrega lote;

    @BeforeEach
    void setUp() {
        service = new ExtratoAcertoServiceImpl(entregaRepository, buscador);
        estabelecimento = Fixtures.estabelecimento();
        motoboy = Fixtures.motoboy(estabelecimento);
        lote = Fixtures.lote(estabelecimento, motoboy, StatusLote.CONCLUIDO);
    }

    private Entrega entrega(String distancia, String de, String ate, String valor) {
        Entrega entrega = Fixtures.entregaFinalizada(estabelecimento, lote, DIA.atTime(20, 0));
        entrega.setDistanciaKm(reais(distancia));
        entrega.setFaixaPreco(de == null ? null : Fixtures.faixa(estabelecimento, de, ate, valor));
        entrega.setValorRepasse(valor == null ? null : reais(valor));
        return entrega;
    }

    private ExtratoAcertoResponse extrato(List<Entrega> entregas) {
        when(buscador.motoboy(estabelecimento.getId(), motoboy.getId())).thenReturn(motoboy);
        when(entregaRepository.findByEstabelecimentoIdAndLoteMotoboyIdAndFinalizadaEmBetweenOrderByFinalizadaEmAsc(
                estabelecimento.getId(), motoboy.getId(), INICIO, FIM)).thenReturn(entregas);
        return service.gerarExtrato(estabelecimento.getId(), motoboy.getId(), INICIO, FIM);
    }

    @Test
    @DisplayName("Deve reproduzir o extrato da HU06: R$ 9 + R$ 12 + R$ 9 = R$ 30, com a faixa em cada linha")
    void deveReproduzirExtratoDaHu06() {
        Entrega a = entrega("2.1", "0", "3", "9.00");
        Entrega b = entrega("4.7", "3", "5", "12.00");
        Entrega c = entrega("1.4", "0", "3", "9.00");
        c.setStatus(StatusEntrega.FALHA);

        ExtratoAcertoResponse extrato = extrato(List.of(a, b, c));

        assertEquals(3, extrato.linhas().size());
        LinhaExtratoResponse linhaB = extrato.linhas().get(1);
        assertEquals(reais("3"), linhaB.faixaDeKm());
        assertEquals(reais("5"), linhaB.faixaAteKm());
        assertEquals(reais("12.00"), linhaB.valorRepasse());
        assertEquals(0, reais("30.00").compareTo(extrato.totalDevido()));
        assertEquals("João", extrato.nomeEntregador());
        assertFalse(extrato.possuiValorIndefinido());
    }

    @Test
    @DisplayName("Deve separar o devido, o já consolidado em recibo e o que está em revisão (UC08)")
    void deveSepararSituacoes() {
        Entrega aAcertar = entrega("2.1", "0", "3", "9.00");
        Entrega consolidada = entrega("4.7", "3", "5", "12.00");
        consolidada.setRecibo(Fixtures.recibo(estabelecimento,
                Fixtures.caixa(estabelecimento, StatusCaixa.ABERTO, DIA), motoboy, StatusRecibo.PAGO));
        Entrega contestada = entrega("6.0", "5", "8", "15.00");
        contestada.contestar();

        ExtratoAcertoResponse extrato = extrato(List.of(aAcertar, consolidada, contestada));

        assertEquals(List.of(SituacaoAcerto.A_ACERTAR, SituacaoAcerto.CONSOLIDADA, SituacaoAcerto.EM_REVISAO),
                extrato.linhas().stream().map(LinhaExtratoResponse::situacao).toList());
        assertEquals(0, reais("9.00").compareTo(extrato.totalDevido()));
        assertEquals(0, reais("12.00").compareTo(extrato.totalConsolidado()));
        assertEquals(0, reais("15.00").compareTo(extrato.totalEmRevisao()));
        assertEquals(consolidada.getRecibo().getId(), extrato.linhas().get(1).reciboId());
    }

    @Test
    @DisplayName("Deve marcar fora de área e alertar valor indefinido, sem confundir com cancelamento antes da saída")
    void deveTratarForaDeAreaECancelamento() {
        Entrega foraDeArea = entrega("9.0", null, null, "15.00");
        Entrega semValorDefinido = entrega("12.0", null, null, null);
        Entrega canceladaNaLoja = entrega("2.1", null, null, "0");
        canceladaNaLoja.setStatus(StatusEntrega.CANCELADA);

        ExtratoAcertoResponse extrato = extrato(List.of(foraDeArea, semValorDefinido, canceladaNaLoja));

        assertTrue(extrato.linhas().get(0).foraDeArea());
        assertNull(extrato.linhas().get(0).faixaDeKm());
        assertTrue(extrato.linhas().get(1).foraDeArea());
        assertFalse(extrato.linhas().get(2).foraDeArea());
        assertTrue(extrato.possuiValorIndefinido());
        assertEquals(0, reais("15.00").compareTo(extrato.totalDevido()));
    }

    @Test
    @DisplayName("Deve ignorar entrega ainda em andamento e recusar período inválido")
    void deveValidarPeriodoEIgnorarEmAndamento() {
        Entrega emRota = entrega("2.1", "0", "3", "9.00");
        emRota.setStatus(StatusEntrega.EM_ROTA);

        assertTrue(extrato(List.of(emRota)).linhas().isEmpty());
        assertThrows(RegraDeNegocioException.class, () ->
                service.gerarExtrato(estabelecimento.getId(), motoboy.getId(), FIM, INICIO));
    }

    @Test
    @DisplayName("Não deve consultar entregas com período ausente")
    void naoDeveConsultarSemPeriodo() {
        assertThrows(RegraDeNegocioException.class, () ->
                service.gerarExtrato(estabelecimento.getId(), motoboy.getId(), null, FIM));
        verifyNoInteractions(entregaRepository, buscador);
    }
}
