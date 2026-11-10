package br.com.agilizadelivery.service;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.dto.request.AbrirCaixaRequest;
import br.com.agilizadelivery.dto.response.CaixaResponse;
import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
import br.com.agilizadelivery.exception.AcessoNegadoException;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.exception.CaixaJaAbertoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.EstabelecimentoInativoException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.repository.ReciboRepository;
import br.com.agilizadelivery.service.impl.CaixaServiceImpl;
import br.com.agilizadelivery.service.support.BuscadorTenant;

@ExtendWith(MockitoExtension.class)
class CaixaServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 9);

    @Mock
    private CaixaRepository caixaRepository;

    @Mock
    private ReciboRepository reciboRepository;

    @Mock
    private BuscadorTenant buscador;

    private CaixaService caixaService;
    private Estabelecimento estabelecimento;
    private UUID estabelecimentoId;
    private Caixa caixa;
    private Usuario operador;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        caixaService = new CaixaServiceImpl(caixaRepository, reciboRepository, buscador);
        estabelecimento = Fixtures.estabelecimento();
        estabelecimentoId = estabelecimento.getId();
        caixa = Fixtures.caixa(estabelecimento, StatusCaixa.ABERTO, HOJE);
        operador = Fixtures.usuario(estabelecimento, PerfilUsuario.OPERADOR);
        admin = Fixtures.usuario(estabelecimento, PerfilUsuario.ADMIN_RESTAURANTE);
    }

    private void caixaEncontrado() {
        when(caixaRepository.findByIdAndEstabelecimentoId(caixa.getId(), estabelecimentoId))
                .thenReturn(Optional.of(caixa));
    }

    @Test
    @DisplayName("Deve abrir o caixa quando o estabelecimento está ativo e a data está livre")
    void deveAbrirCaixaComSucesso() {
        when(buscador.estabelecimentoAtivo(estabelecimentoId)).thenReturn(estabelecimento);
        when(caixaRepository.existsByEstabelecimentoIdAndDataReferencia(estabelecimentoId, HOJE)).thenReturn(false);
        when(caixaRepository.save(any(Caixa.class))).then(returnsFirstArg());

        CaixaResponse resultado = caixaService.abrirCaixa(estabelecimentoId, new AbrirCaixaRequest(HOJE));

        assertEquals(StatusCaixa.ABERTO, resultado.status());
        assertEquals(HOJE, resultado.dataReferencia());
        assertEquals(estabelecimentoId, resultado.estabelecimentoId());
        assertNotNull(resultado.abertoEm());
    }

    @Test
    @DisplayName("Deve propagar EstabelecimentoInativoException ao abrir caixa de loja inativa")
    void deveRecusarEstabelecimentoInativo() {
        when(buscador.estabelecimentoAtivo(estabelecimentoId))
                .thenThrow(new EstabelecimentoInativoException("Pizzaria"));

        assertThrows(EstabelecimentoInativoException.class, () ->
                caixaService.abrirCaixa(estabelecimentoId, new AbrirCaixaRequest(HOJE)));
        verify(caixaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar CaixaJaAbertoException quando o caixa do dia já existe")
    void deveRecusarSegundoCaixaNoDia() {
        when(buscador.estabelecimentoAtivo(estabelecimentoId)).thenReturn(estabelecimento);
        when(caixaRepository.existsByEstabelecimentoIdAndDataReferencia(estabelecimentoId, HOJE)).thenReturn(true);

        assertThrows(CaixaJaAbertoException.class, () ->
                caixaService.abrirCaixa(estabelecimentoId, new AbrirCaixaRequest(HOJE)));
        verify(caixaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve fechar o caixa consolidando apenas recibos não cancelados")
    void deveFecharCaixaConsolidando() {
        caixaEncontrado();
        when(buscador.usuarioComPerfil(estabelecimentoId, operador.getId(),
                PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE)).thenReturn(operador);
        Motoboy motoboy = Fixtures.motoboy(estabelecimento);
        Recibo pago = Fixtures.recibo(estabelecimento, caixa, motoboy, StatusRecibo.PAGO);
        pago.setValorTotal(reais("30.00"));
        Recibo gerado = Fixtures.recibo(estabelecimento, caixa, motoboy, StatusRecibo.GERADO);
        gerado.setValorTotal(reais("12.00"));
        Recibo cancelado = Fixtures.recibo(estabelecimento, caixa, motoboy, StatusRecibo.CANCELADO);
        cancelado.setValorTotal(reais("99.00"));
        when(reciboRepository.findByEstabelecimentoIdAndCaixaId(estabelecimentoId, caixa.getId()))
                .thenReturn(List.of(pago, gerado, cancelado));
        when(caixaRepository.save(any(Caixa.class))).then(returnsFirstArg());

        CaixaResponse fechado = caixaService.fecharCaixa(estabelecimentoId, caixa.getId(), operador.getId());

        assertEquals(StatusCaixa.FECHADO, fechado.status());
        assertEquals((short) 2, fechado.qtdRecibos());
        assertEquals(reais("42.00"), fechado.valorTotal());
        assertEquals(operador.getId(), fechado.usuarioFechamentoId());
        assertNotNull(fechado.fechadoEm());
    }

    @Test
    @DisplayName("Deve lançar CaixaFechadoException ao fechar caixa já fechado")
    void deveRecusarFecharCaixaFechado() {
        caixa.setStatus(StatusCaixa.FECHADO);
        caixaEncontrado();

        assertThrows(CaixaFechadoException.class, () ->
                caixaService.fecharCaixa(estabelecimentoId, caixa.getId(), operador.getId()));
    }

    @Test
    @DisplayName("Deve reabrir caixa fechado pelo administrador do restaurante, com justificativa")
    void deveReabrirCaixa() {
        caixa.setStatus(StatusCaixa.FECHADO);
        caixaEncontrado();
        when(buscador.usuarioComPerfil(estabelecimentoId, admin.getId(), PerfilUsuario.ADMIN_RESTAURANTE))
                .thenReturn(admin);
        when(caixaRepository.save(any(Caixa.class))).then(returnsFirstArg());

        CaixaResponse reaberto = caixaService.reabrirCaixa(
                estabelecimentoId, caixa.getId(), admin.getId(), "  Correção de entrega duplicada ");

        assertEquals(StatusCaixa.REABERTO, reaberto.status());
        assertEquals("Correção de entrega duplicada", reaberto.motivoReabertura());
        assertEquals(admin.getId(), reaberto.usuarioReaberturaId());
        assertNotNull(reaberto.reabertoEm());
    }

    @Test
    @DisplayName("Deve recusar reabertura por quem não é administrador do restaurante (UC03 A3)")
    void deveRecusarReaberturaPorOperador() {
        caixa.setStatus(StatusCaixa.FECHADO);
        caixaEncontrado();
        when(buscador.usuarioComPerfil(estabelecimentoId, operador.getId(), PerfilUsuario.ADMIN_RESTAURANTE))
                .thenThrow(new AcessoNegadoException("sem permissão"));

        assertThrows(AcessoNegadoException.class, () ->
                caixaService.reabrirCaixa(estabelecimentoId, caixa.getId(), operador.getId(), "motivo"));
        verify(caixaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar TransicaoEstadoInvalidaException ao reabrir caixa que não está fechado")
    void deveRecusarReaberturaDeCaixaAberto() {
        caixaEncontrado();

        assertThrows(TransicaoEstadoInvalidaException.class, () ->
                caixaService.reabrirCaixa(estabelecimentoId, caixa.getId(), admin.getId(), "Tentativa indevida"));
    }

    @Test
    @DisplayName("Deve exigir motivo para reabrir o caixa")
    void deveExigirMotivoNaReabertura() {
        caixa.setStatus(StatusCaixa.FECHADO);
        caixaEncontrado();

        assertThrows(RegraDeNegocioException.class, () ->
                caixaService.reabrirCaixa(estabelecimentoId, caixa.getId(), admin.getId(), "   "));
    }

    @Test
    @DisplayName("Deve tratar caixa de outra loja como inexistente")
    void deveIsolarCaixaDeOutraLoja() {
        when(caixaRepository.findByIdAndEstabelecimentoId(caixa.getId(), estabelecimentoId))
                .thenReturn(Optional.empty());

        assertThrows(EntidadeNaoEncontradaException.class, () ->
                caixaService.buscarPorId(estabelecimentoId, caixa.getId()));
    }

    @Test
    @DisplayName("Deve buscar o caixa pela data e listar por status")
    void deveConsultarCaixas() {
        when(caixaRepository.findByEstabelecimentoIdAndDataReferencia(estabelecimentoId, HOJE))
                .thenReturn(Optional.of(caixa));
        when(caixaRepository.findByEstabelecimentoIdAndStatus(estabelecimentoId, StatusCaixa.ABERTO))
                .thenReturn(List.of(caixa));

        assertEquals(caixa.getId(), caixaService.buscarPorEstabelecimentoEData(estabelecimentoId, HOJE).id());
        assertEquals(1, caixaService.listarPorEstabelecimentoEStatus(estabelecimentoId, StatusCaixa.ABERTO).size());
        assertThrows(EntidadeNaoEncontradaException.class, () ->
                caixaService.buscarPorEstabelecimentoEData(estabelecimentoId, HOJE.plusDays(1)));
    }
}
