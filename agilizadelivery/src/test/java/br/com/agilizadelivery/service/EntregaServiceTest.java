package br.com.agilizadelivery.service;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.dto.request.AlterarStatusEntregaRequest;
import br.com.agilizadelivery.dto.request.CriarEntregaAvulsaRequest;
import br.com.agilizadelivery.dto.response.EntregaResponse;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.EntregaStatusHistorico;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.FormaPagamento;
import br.com.agilizadelivery.entity.model.enums.MotivoRevisao;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.exception.AcessoNegadoException;
import br.com.agilizadelivery.exception.AjusteValorNaoPermitidoException;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.EntregaStatusHistoricoRepository;
import br.com.agilizadelivery.service.impl.EntregaServiceImpl;
import br.com.agilizadelivery.service.support.AjusteRepasse;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.service.support.CalculadoraRepasse;
import br.com.agilizadelivery.service.support.EncerramentoLote;
import br.com.agilizadelivery.service.support.LocalizacaoEntrega;
import br.com.agilizadelivery.service.support.PoliticaCorrecao;
import br.com.agilizadelivery.service.support.TransicaoEntrega;

@ExtendWith(MockitoExtension.class)
class EntregaServiceTest {

    private static final LocalDateTime OCORRIDO_EM = LocalDate.of(2026, 11, 9).atTime(20, 40);

    @Mock
    private EntregaRepository repository;

    @Mock
    private EntregaStatusHistoricoRepository historicoRepository;

    @Mock
    private BuscadorTenant buscador;

    @Mock
    private LocalizacaoEntrega localizacao;

    @Mock
    private CalculadoraRepasse calculadoraRepasse;

    @Mock
    private EncerramentoLote encerramentoLote;

    @Mock
    private PoliticaCorrecao politicaCorrecao;

    @Mock
    private AjusteRepasse ajusteRepasse;

    @Mock
    private br.com.agilizadelivery.websocket.PublicadorTempoReal publicador;

    private EntregaService service;
    private Estabelecimento estabelecimento;
    private Motoboy motoboy;
    private LoteEntrega lote;
    private Usuario operador;

    @BeforeEach
    void setUp() {
        service = new EntregaServiceImpl(repository, buscador, localizacao, calculadoraRepasse,
                new TransicaoEntrega(historicoRepository), encerramentoLote, politicaCorrecao, ajusteRepasse, publicador,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        estabelecimento = Fixtures.estabelecimento();
        motoboy = Fixtures.motoboy(estabelecimento);
        lote = Fixtures.lote(estabelecimento, motoboy, StatusLote.EM_ROTA);
        operador = Fixtures.usuario(estabelecimento, PerfilUsuario.OPERADOR);
        lenient().when(repository.save(any(Entrega.class))).then(returnsFirstArg());
    }

    private Entrega encontrada(StatusEntrega status) {
        Entrega entrega = Fixtures.entrega(estabelecimento, status);
        entrega.setLote(lote);
        when(repository.findByIdAndEstabelecimentoId(entrega.getId(), estabelecimento.getId()))
                .thenReturn(Optional.of(entrega));
        return entrega;
    }

    private void operadorAutorizado() {
        when(buscador.usuarioComPerfil(estabelecimento.getId(), operador.getId(),
                PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE)).thenReturn(operador);
    }

    private AlterarStatusEntregaRequest finalizar(StatusEntrega status, String motivo) {
        return new AlterarStatusEntregaRequest(status, motivo,
                new CoordenadaDto(reais("-23.56"), reais("-46.65")), OCORRIDO_EM);
    }

    @Test
    @DisplayName("Deve criar entrega avulsa localizada, aguardando despacho e com histórico inicial (UC01 A2)")
    void deveCriarAvulsa() {
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        CriarEntregaAvulsaRequest request = new CriarEntregaAvulsaRequest("Maria", "11977776666",
                "Rua A, 10", "Centro", "01000-000", null, null,
                new CoordenadaDto(reais("-23.56"), reais("-46.65")),
                reais("80.00"), FormaPagamento.DINHEIRO, null, reais("100.00"), "Sem cebola");

        EntregaResponse criada = service.criarAvulsa(estabelecimento.getId(), request);

        assertEquals(StatusEntrega.AGUARDANDO_DESPACHO, criada.status());
        assertFalse(criada.pagoOnline());
        assertNull(criada.pedidoExternoId());
        assertEquals(reais("-23.56"), criada.coordenada().latitude());
        verify(localizacao).aplicar(any(Entrega.class), any());
        ArgumentCaptor<EntregaStatusHistorico> historico = ArgumentCaptor.forClass(EntregaStatusHistorico.class);
        verify(historicoRepository).save(historico.capture());
        assertNull(historico.getValue().getStatusAnterior());
    }

    @Test
    @DisplayName("Deve criar entrega avulsa sem coordenada, deixando a geocodificação para a localização")
    void deveCriarAvulsaSemCoordenada() {
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        CriarEntregaAvulsaRequest request = new CriarEntregaAvulsaRequest("Maria", null, "Rua A, 10",
                null, null, null, null, null, null, null, true, null, null);

        EntregaResponse criada = service.criarAvulsa(estabelecimento.getId(), request);

        assertNull(criada.coordenada());
        assertTrue(criada.pagoOnline());
        verify(localizacao).aplicar(any(Entrega.class), any());
    }

    @Test
    @DisplayName("Deve recusar coordenada inválida na entrega avulsa")
    void deveRecusarCoordenadaInvalida() {
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        CriarEntregaAvulsaRequest request = new CriarEntregaAvulsaRequest("Maria", null, "Rua A, 10",
                null, null, null, null, new CoordenadaDto(reais("-95"), reais("0")),
                null, null, null, null, null);

        assertThrows(RegraDeNegocioException.class, () -> service.criarAvulsa(estabelecimento.getId(), request));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve listar pendentes de despacho sem revisão em aberto (HU01)")
    void deveListarPendentes() {
        Entrega entrega = Fixtures.entrega(estabelecimento, StatusEntrega.AGUARDANDO_DESPACHO);
        when(repository.findByEstabelecimentoIdAndStatusAndPendenteRevisaoFalseOrderByCriadoEmAsc(
                estabelecimento.getId(), StatusEntrega.AGUARDANDO_DESPACHO)).thenReturn(List.of(entrega));
        when(repository.findByEstabelecimentoIdAndStatusOrderByCriadoEmAsc(
                estabelecimento.getId(), StatusEntrega.ENTREGUE)).thenReturn(List.of());

        assertEquals(1, service.listarPendentesDeDespacho(estabelecimento.getId()).size());
        assertTrue(service.listarPorEstabelecimentoEStatus(estabelecimento.getId(), StatusEntrega.ENTREGUE).isEmpty());
    }

    @Test
    @DisplayName("Deve listar o extrato do entregador e validar o período (UC08)")
    void deveListarDoMotoboy() {
        LocalDateTime inicio = OCORRIDO_EM.minusHours(3);
        when(repository.findByEstabelecimentoIdAndLoteMotoboyIdAndFinalizadaEmBetweenOrderByFinalizadaEmAsc(
                estabelecimento.getId(), motoboy.getId(), inicio, OCORRIDO_EM))
                .thenReturn(List.of(Fixtures.entregaFinalizada(estabelecimento, lote, OCORRIDO_EM)));

        assertEquals(1, service.listarDoMotoboy(estabelecimento.getId(), motoboy.getId(), inicio, OCORRIDO_EM).size());
        verify(buscador).motoboy(estabelecimento.getId(), motoboy.getId());
        assertThrows(RegraDeNegocioException.class, () ->
                service.listarDoMotoboy(estabelecimento.getId(), motoboy.getId(), OCORRIDO_EM, inicio));
    }

    @Test
    @DisplayName("Deve marcar o pedido como pronto apenas enquanto aguarda despacho")
    void deveMarcarPronta() {
        Entrega entrega = encontrada(StatusEntrega.AGUARDANDO_DESPACHO);

        assertEquals(OCORRIDO_EM, service.marcarProntaParaDespacho(
                estabelecimento.getId(), entrega.getId(), OCORRIDO_EM).prontoParaDespachoEm());

        entrega.setStatus(StatusEntrega.DESPACHADA);
        assertThrows(TransicaoEstadoInvalidaException.class, () ->
                service.marcarProntaParaDespacho(estabelecimento.getId(), entrega.getId(), null));
    }

    @Test
    @DisplayName("Deve registrar ENTREGUE, congelar o repasse e avaliar o encerramento do lote")
    void deveFinalizarComoEntregue() {
        Entrega entrega = encontrada(StatusEntrega.EM_ROTA);
        Usuario entregador = motoboy.getUsuario();
        when(buscador.usuario(estabelecimento.getId(), entregador.getId())).thenReturn(entregador);

        EntregaResponse resposta = service.alterarStatus(estabelecimento.getId(), entrega.getId(),
                entregador.getId(), finalizar(StatusEntrega.ENTREGUE, null));

        assertEquals(StatusEntrega.ENTREGUE, resposta.status());
        assertEquals(OCORRIDO_EM, resposta.finalizadaEm());
        verify(calculadoraRepasse).aplicarFaixaVigente(entrega);
        verify(encerramentoLote).avaliar(lote);
        ArgumentCaptor<EntregaStatusHistorico> historico = ArgumentCaptor.forClass(EntregaStatusHistorico.class);
        verify(historicoRepository).save(historico.capture());
        assertEquals(OCORRIDO_EM, historico.getValue().getRegistradoEm());
        assertNotNull(historico.getValue().getCoordenada());
    }

    @Test
    @DisplayName("Deve exigir motivo para FALHA e manter repasse integral (UC02 A1)")
    void deveFinalizarComoFalha() {
        Entrega entrega = encontrada(StatusEntrega.EM_ROTA);
        Usuario entregador = motoboy.getUsuario();
        when(buscador.usuario(estabelecimento.getId(), entregador.getId())).thenReturn(entregador);

        assertThrows(RegraDeNegocioException.class, () -> service.alterarStatus(estabelecimento.getId(),
                entrega.getId(), entregador.getId(), finalizar(StatusEntrega.FALHA, " ")));

        EntregaResponse resposta = service.alterarStatus(estabelecimento.getId(), entrega.getId(),
                entregador.getId(), finalizar(StatusEntrega.FALHA, "Cliente ausente"));
        assertEquals(StatusEntrega.FALHA, resposta.status());
        assertEquals("Cliente ausente", resposta.motivoFalha());
        verify(calculadoraRepasse).aplicarFaixaVigente(entrega);
    }

    @Test
    @DisplayName("Deve aceitar apenas ENTREGUE ou FALHA nesta operação")
    void deveRecusarOutrosDestinos() {
        assertThrows(TransicaoEstadoInvalidaException.class, () -> service.alterarStatus(
                estabelecimento.getId(), lote.getId(), operador.getId(), finalizar(StatusEntrega.CANCELADA, null)));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Deve recusar transição fora da máquina de estados")
    void deveRecusarTransicaoInvalida() {
        Entrega entrega = encontrada(StatusEntrega.DESPACHADA);
        when(buscador.usuario(estabelecimento.getId(), operador.getId())).thenReturn(operador);

        assertThrows(TransicaoEstadoInvalidaException.class, () -> service.alterarStatus(estabelecimento.getId(),
                entrega.getId(), operador.getId(), finalizar(StatusEntrega.ENTREGUE, null)));
    }

    @Test
    @DisplayName("Deve impedir que um entregador altere entrega de outro lote")
    void deveRecusarEntregadorDeOutroLote() {
        Entrega entrega = encontrada(StatusEntrega.EM_ROTA);
        Usuario outro = Fixtures.motoboy(estabelecimento).getUsuario();
        when(buscador.usuario(estabelecimento.getId(), outro.getId())).thenReturn(outro);

        assertThrows(AcessoNegadoException.class, () -> service.alterarStatus(estabelecimento.getId(),
                entrega.getId(), outro.getId(), finalizar(StatusEntrega.ENTREGUE, null)));
    }

    @Test
    @DisplayName("Deve zerar o repasse ao cancelar antes da saída da loja (DVP 1.5.5 passo 5)")
    void deveCancelarAntesDaSaida() {
        Entrega entrega = encontrada(StatusEntrega.AGUARDANDO_DESPACHO);
        entrega.setPendenteRevisao(true);
        entrega.setMotivoRevisao(MotivoRevisao.SEM_DISTANCIA);
        operadorAutorizado();

        EntregaResponse cancelada = service.cancelar(
                estabelecimento.getId(), entrega.getId(), operador.getId(), "Cliente desistiu");

        assertEquals(StatusEntrega.CANCELADA, cancelada.status());
        assertFalse(cancelada.pendenteRevisao());
        assertNotNull(cancelada.finalizadaEm());
        verify(calculadoraRepasse).zerarRepasse(entrega);
        verify(calculadoraRepasse, never()).aplicarFaixaVigente(any());
    }

    @Test
    @DisplayName("Deve manter repasse integral ao cancelar durante o percurso (UC02 A3)")
    void deveCancelarEmRota() {
        Entrega entrega = encontrada(StatusEntrega.EM_ROTA);
        operadorAutorizado();

        service.cancelar(estabelecimento.getId(), entrega.getId(), operador.getId(), "Cancelado no iFood");

        verify(calculadoraRepasse).aplicarFaixaVigente(entrega);
        verify(calculadoraRepasse, never()).zerarRepasse(any());
        verify(encerramentoLote).avaliar(lote);
    }

    @Test
    @DisplayName("Deve exigir motivo e recusar cancelamento de entrega finalizada")
    void deveValidarCancelamento() {
        assertThrows(RegraDeNegocioException.class, () ->
                service.cancelar(estabelecimento.getId(), lote.getId(), operador.getId(), ""));

        Entrega entrega = encontrada(StatusEntrega.ENTREGUE);
        operadorAutorizado();
        assertThrows(TransicaoEstadoInvalidaException.class, () ->
                service.cancelar(estabelecimento.getId(), entrega.getId(), operador.getId(), "motivo"));
    }

    @Test
    @DisplayName("Deve reposicionar o pino e recalcular a localização (UC01 A1)")
    void deveAjustarCoordenada() {
        Entrega entrega = encontrada(StatusEntrega.AGUARDANDO_DESPACHO);
        operadorAutorizado();

        EntregaResponse ajustada = service.ajustarCoordenada(
                estabelecimento.getId(), entrega.getId(), reais("-23.6"), reais("-46.7"), operador.getId());

        assertEquals(reais("-23.6"), ajustada.coordenada().latitude());
        verify(localizacao).aplicar(eq(entrega), any());
        verify(encerramentoLote).atualizarTotal(lote);
    }

    @Test
    @DisplayName("Não deve reposicionar entrega finalizada")
    void naoDeveAjustarCoordenadaDeEntregaFinal() {
        Entrega entrega = encontrada(StatusEntrega.ENTREGUE);
        operadorAutorizado();

        assertThrows(RegraDeNegocioException.class, () -> service.ajustarCoordenada(
                estabelecimento.getId(), entrega.getId(), reais("-23.6"), reais("-46.7"), operador.getId()));
        verifyNoInteractions(localizacao);
    }

    @Test
    @DisplayName("Deve marcar a entrega para revisão quando o entregador contesta (UC08 A1)")
    void deveContestar() {
        Entrega entrega = encontrada(StatusEntrega.ENTREGUE);
        entrega.setFinalizadaEm(OCORRIDO_EM);

        EntregaResponse contestada = service.contestar(estabelecimento.getId(), entrega.getId(), motoboy.getId());

        assertTrue(contestada.pendenteRevisao());
        assertEquals(MotivoRevisao.CONTESTACAO_ENTREGADOR, contestada.motivoRevisao());
        verify(politicaCorrecao).exigirCaixaDoDiaAberto(any(), any());
    }

    @Test
    @DisplayName("Deve recusar contestação repetida (UC08 E1), de entrega em andamento ou de outro entregador")
    void deveValidarContestacao() {
        Entrega entrega = encontrada(StatusEntrega.ENTREGUE);

        assertThrows(AcessoNegadoException.class, () ->
                service.contestar(estabelecimento.getId(), entrega.getId(), Fixtures.motoboy(estabelecimento).getId()));

        entrega.contestar();
        assertThrows(RegraDeNegocioException.class, () ->
                service.contestar(estabelecimento.getId(), entrega.getId(), motoboy.getId()));

        entrega.setPendenteRevisao(false);
        entrega.setStatus(StatusEntrega.EM_ROTA);
        assertThrows(RegraDeNegocioException.class, () ->
                service.contestar(estabelecimento.getId(), entrega.getId(), motoboy.getId()));
    }

    @Test
    @DisplayName("Deve recusar contestação com o caixa do dia fechado (UC08 E2)")
    void deveRecusarContestacaoComCaixaFechado() {
        Entrega entrega = encontrada(StatusEntrega.ENTREGUE);
        doThrow(new CaixaFechadoException("contestar")).when(politicaCorrecao).exigirCaixaDoDiaAberto(any(), any());

        assertThrows(CaixaFechadoException.class, () ->
                service.contestar(estabelecimento.getId(), entrega.getId(), motoboy.getId()));
        assertFalse(entrega.getPendenteRevisao());
    }

    @Test
    @DisplayName("Deve resolver a contestação procedente ajustando o valor pelo UC03 A1")
    void deveResolverRevisaoComAjuste() {
        Entrega entrega = encontrada(StatusEntrega.ENTREGUE);
        entrega.contestar();
        operadorAutorizado();

        EntregaResponse resolvida = service.resolverRevisao(
                estabelecimento.getId(), entrega.getId(), operador.getId(), reais("12.00"), "Distância conferida");

        verify(ajusteRepasse).ajustar(entrega, operador, reais("12.00"), "Distância conferida");
        assertFalse(resolvida.pendenteRevisao());
        assertNull(resolvida.motivoRevisao());
    }

    @Test
    @DisplayName("Deve resolver a contestação improcedente mantendo o valor")
    void deveResolverRevisaoSemAjuste() {
        Entrega entrega = encontrada(StatusEntrega.ENTREGUE);
        entrega.contestar();
        operadorAutorizado();

        service.resolverRevisao(estabelecimento.getId(), entrega.getId(), operador.getId(), reais("9.00"), "Valor ok");

        verifyNoInteractions(ajusteRepasse);
        verify(politicaCorrecao).exigirCaixaDoDiaAberto(any(), any());
        assertFalse(entrega.getPendenteRevisao());
    }

    @Test
    @DisplayName("Deve manter a revisão pendente quando o ajuste é recusado por estar em recibo (DVP 2.9)")
    void deveManterRevisaoQuandoAjusteRecusado() {
        Entrega entrega = encontrada(StatusEntrega.ENTREGUE);
        entrega.contestar();
        operadorAutorizado();
        when(ajusteRepasse.ajustar(any(), any(), any(), any()))
                .thenThrow(new AjusteValorNaoPermitidoException("a entrega está vinculada a recibo"));

        assertThrows(AjusteValorNaoPermitidoException.class, () -> service.resolverRevisao(
                estabelecimento.getId(), entrega.getId(), operador.getId(), reais("12.00"), "motivo"));
        assertTrue(entrega.getPendenteRevisao());
    }

    @Test
    @DisplayName("Deve recusar resolução sem pendência, sem motivo ou de pendência de localização")
    void deveValidarResolucao() {
        assertThrows(RegraDeNegocioException.class, () -> service.resolverRevisao(
                estabelecimento.getId(), lote.getId(), operador.getId(), null, " "));

        Entrega entrega = encontrada(StatusEntrega.ENTREGUE);
        operadorAutorizado();
        assertThrows(RegraDeNegocioException.class, () -> service.resolverRevisao(
                estabelecimento.getId(), entrega.getId(), operador.getId(), null, "motivo"));

        entrega.setPendenteRevisao(true);
        entrega.setMotivoRevisao(MotivoRevisao.SEM_COORDENADA);
        assertThrows(RegraDeNegocioException.class, () -> service.resolverRevisao(
                estabelecimento.getId(), entrega.getId(), operador.getId(), null, "motivo"));
    }
}
