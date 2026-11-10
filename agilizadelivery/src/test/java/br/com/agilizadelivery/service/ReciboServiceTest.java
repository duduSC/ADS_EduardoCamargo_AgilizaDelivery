package br.com.agilizadelivery.service;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.dto.request.GerarReciboRequest;
import br.com.agilizadelivery.dto.response.ReciboResponse;
import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.EntregaInaptaParaReciboException;
import br.com.agilizadelivery.exception.ReciboInalteravelException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.ReciboRepository;
import br.com.agilizadelivery.service.impl.ReciboServiceImpl;
import br.com.agilizadelivery.service.support.BuscadorTenant;

@ExtendWith(MockitoExtension.class)
class ReciboServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 9);
    private static final LocalDateTime INICIO = DIA.atTime(18, 0);
    private static final LocalDateTime FIM = DIA.atTime(23, 59);

    @Mock
    private ReciboRepository repository;

    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private CaixaRepository caixaRepository;

    @Mock
    private BuscadorTenant buscador;

    private ReciboService service;
    private Estabelecimento estabelecimento;
    private Motoboy motoboy;
    private Usuario operador;
    private Caixa caixa;
    private LoteEntrega lote;

    @BeforeEach
    void setUp() {
        service = new ReciboServiceImpl(repository, entregaRepository, caixaRepository, buscador);
        estabelecimento = Fixtures.estabelecimento();
        motoboy = Fixtures.motoboy(estabelecimento);
        operador = Fixtures.usuario(estabelecimento, PerfilUsuario.OPERADOR);
        caixa = Fixtures.caixa(estabelecimento, StatusCaixa.ABERTO, DIA);
        lote = Fixtures.lote(estabelecimento, motoboy, StatusLote.CONCLUIDO);
        lenient().when(buscador.usuarioComPerfil(estabelecimento.getId(), operador.getId(),
                PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE)).thenReturn(operador);
        lenient().when(repository.save(any(Recibo.class))).then(returnsFirstArg());
    }

    private void prepararGeracao(List<Entrega> doPeriodo) {
        when(buscador.motoboy(estabelecimento.getId(), motoboy.getId())).thenReturn(motoboy);
        when(caixaRepository.findByEstabelecimentoIdAndDataReferencia(estabelecimento.getId(), DIA))
                .thenReturn(Optional.of(caixa));
        lenient().when(entregaRepository
                .findByEstabelecimentoIdAndLoteMotoboyIdAndFinalizadaEmBetweenOrderByFinalizadaEmAsc(
                        estabelecimento.getId(), motoboy.getId(), INICIO, FIM))
                .thenReturn(doPeriodo);
    }

    private Entrega finalizada(String valor) {
        Entrega entrega = Fixtures.entregaFinalizada(estabelecimento, lote, DIA.atTime(20, 0));
        entrega.setValorRepasse(reais(valor));
        return entrega;
    }

    private GerarReciboRequest periodo() {
        return new GerarReciboRequest(motoboy.getId(), INICIO, FIM);
    }

    @Test
    @DisplayName("Deve gerar o recibo somando apenas entregas aptas e vinculando-as (UC03, HU07)")
    void deveGerarRecibo() {
        Entrega a = finalizada("9.00");
        Entrega b = finalizada("12.00");
        Entrega contestada = finalizada("9.00");
        contestada.contestar();
        Entrega emOutroRecibo = finalizada("9.00");
        emOutroRecibo.setRecibo(Fixtures.recibo(estabelecimento, caixa, motoboy, StatusRecibo.GERADO));
        Entrega emRota = finalizada("9.00");
        emRota.setStatus(StatusEntrega.EM_ROTA);
        prepararGeracao(List.of(a, b, contestada, emOutroRecibo, emRota));

        ReciboResponse recibo = service.gerar(estabelecimento.getId(), operador.getId(), periodo());

        assertEquals(StatusRecibo.GERADO, recibo.status());
        assertEquals((short) 2, recibo.qtdEntregas());
        assertEquals(reais("21.00"), recibo.valorTotal());
        assertEquals(caixa.getId(), recibo.caixaId());
        assertNotNull(a.getRecibo());
        assertSame(a.getRecibo(), b.getRecibo());
        assertNull(contestada.getRecibo());
        verify(entregaRepository).saveAll(List.of(a, b));
    }

    @Test
    @DisplayName("Deve recusar geração sem entregas aptas no período")
    void deveRecusarSemEntregasAptas() {
        Entrega contestada = finalizada("9.00");
        contestada.contestar();
        prepararGeracao(List.of(contestada));

        assertThrows(RegraDeNegocioException.class, () ->
                service.gerar(estabelecimento.getId(), operador.getId(), periodo()));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve recusar entrega sem valor de repasse definido (fora de área sem valor na loja)")
    void deveRecusarEntregaSemRepasse() {
        Entrega semValor = finalizada("9.00");
        semValor.setValorRepasse(null);
        prepararGeracao(List.of(semValor));

        assertThrows(EntregaInaptaParaReciboException.class, () ->
                service.gerar(estabelecimento.getId(), operador.getId(), periodo()));
    }

    @Test
    @DisplayName("Deve exigir período válido e caixa do dia aberto")
    void deveValidarPeriodoECaixa() {
        assertThrows(RegraDeNegocioException.class, () -> service.gerar(estabelecimento.getId(), operador.getId(),
                new GerarReciboRequest(motoboy.getId(), FIM, INICIO)));

        when(buscador.motoboy(estabelecimento.getId(), motoboy.getId())).thenReturn(motoboy);
        when(caixaRepository.findByEstabelecimentoIdAndDataReferencia(estabelecimento.getId(), DIA))
                .thenReturn(Optional.empty());
        assertThrows(EntidadeNaoEncontradaException.class, () ->
                service.gerar(estabelecimento.getId(), operador.getId(), periodo()));

        caixa.setStatus(StatusCaixa.FECHADO);
        when(caixaRepository.findByEstabelecimentoIdAndDataReferencia(estabelecimento.getId(), DIA))
                .thenReturn(Optional.of(caixa));
        assertThrows(CaixaFechadoException.class, () ->
                service.gerar(estabelecimento.getId(), operador.getId(), periodo()));
    }

    private Recibo encontrado(StatusRecibo status) {
        Recibo recibo = Fixtures.recibo(estabelecimento, caixa, motoboy, status);
        when(repository.findByIdAndEstabelecimentoId(recibo.getId(), estabelecimento.getId()))
                .thenReturn(Optional.of(recibo));
        return recibo;
    }

    @Test
    @DisplayName("Deve marcar o recibo como pago (UC03 passo 6)")
    void deveMarcarComoPago() {
        Recibo recibo = encontrado(StatusRecibo.GERADO);

        ReciboResponse pago = service.marcarComoPago(estabelecimento.getId(), recibo.getId(), operador.getId());

        assertEquals(StatusRecibo.PAGO, pago.status());
        assertNotNull(pago.pagoEm());
    }

    @Test
    @DisplayName("Deve recusar pagamento de recibo não GERADO ou com caixa fechado")
    void deveValidarPagamento() {
        Recibo recibo = encontrado(StatusRecibo.PAGO);
        assertThrows(TransicaoEstadoInvalidaException.class, () ->
                service.marcarComoPago(estabelecimento.getId(), recibo.getId(), operador.getId()));

        recibo.setStatus(StatusRecibo.GERADO);
        caixa.setStatus(StatusCaixa.FECHADO);
        assertThrows(CaixaFechadoException.class, () ->
                service.marcarComoPago(estabelecimento.getId(), recibo.getId(), operador.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = StatusRecibo.class, names = {"GERADO", "PAGO"})
    @DisplayName("Deve cancelar recibo, mesmo pago, com caixa aberto, desvinculando as entregas (UC03 A2)")
    void deveCancelarRecibo(StatusRecibo status) {
        Recibo recibo = encontrado(status);
        Entrega entrega = finalizada("9.00");
        entrega.setRecibo(recibo);
        when(entregaRepository.findByReciboId(recibo.getId())).thenReturn(List.of(entrega));

        ReciboResponse cancelado = service.cancelar(
                estabelecimento.getId(), recibo.getId(), operador.getId(), "Divergência no valor");

        assertEquals(StatusRecibo.CANCELADO, cancelado.status());
        assertNull(entrega.getRecibo());
        verify(entregaRepository).saveAll(List.of(entrega));
    }

    @Test
    @DisplayName("Deve recusar cancelamento sem motivo, repetido ou com caixa fechado")
    void deveValidarCancelamento() {
        Recibo recibo = encontrado(StatusRecibo.CANCELADO);
        assertThrows(RegraDeNegocioException.class, () ->
                service.cancelar(estabelecimento.getId(), recibo.getId(), operador.getId(), " "));
        assertThrows(ReciboInalteravelException.class, () ->
                service.cancelar(estabelecimento.getId(), recibo.getId(), operador.getId(), "motivo"));

        recibo.setStatus(StatusRecibo.PAGO);
        caixa.setStatus(StatusCaixa.FECHADO);
        assertThrows(ReciboInalteravelException.class, () ->
                service.cancelar(estabelecimento.getId(), recibo.getId(), operador.getId(), "motivo"));
        verify(entregaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Deve consultar recibos da loja")
    void deveConsultar() {
        Recibo recibo = encontrado(StatusRecibo.GERADO);
        when(repository.findByEstabelecimentoIdAndCaixaId(estabelecimento.getId(), caixa.getId()))
                .thenReturn(List.of(recibo));
        when(repository.findByEstabelecimentoIdAndMotoboyIdAndStatus(
                estabelecimento.getId(), motoboy.getId(), StatusRecibo.GERADO)).thenReturn(List.of(recibo));
        when(repository.findByEstabelecimentoIdAndDataReferencia(estabelecimento.getId(), DIA))
                .thenReturn(List.of(recibo));

        assertEquals(recibo.getId(), service.buscarPorId(estabelecimento.getId(), recibo.getId()).id());
        assertEquals(1, service.listarPorCaixa(estabelecimento.getId(), caixa.getId()).size());
        assertEquals(1, service.listarPorEntregadorEStatus(
                estabelecimento.getId(), motoboy.getId(), StatusRecibo.GERADO).size());
        assertEquals(1, service.listarPorDataReferencia(estabelecimento.getId(), DIA).size());
    }
}
