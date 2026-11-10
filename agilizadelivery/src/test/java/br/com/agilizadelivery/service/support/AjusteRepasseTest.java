package br.com.agilizadelivery.service.support;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.EntregaAjusteValor;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.exception.AjusteValorNaoPermitidoException;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.repository.EntregaAjusteValorRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.service.Fixtures;

@ExtendWith(MockitoExtension.class)
class AjusteRepasseTest {

    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private EntregaAjusteValorRepository ajusteRepository;

    @Mock
    private PoliticaCorrecao politicaCorrecao;

    @Mock
    private EncerramentoLote encerramentoLote;

    private AjusteRepasse ajusteRepasse;
    private Entrega entrega;
    private Usuario operador;

    @BeforeEach
    void setUp() {
        ajusteRepasse = new AjusteRepasse(entregaRepository, ajusteRepository, politicaCorrecao, encerramentoLote);
        Estabelecimento estabelecimento = Fixtures.estabelecimento();
        LoteEntrega lote = Fixtures.lote(estabelecimento, Fixtures.motoboy(estabelecimento), StatusLote.CONCLUIDO);
        entrega = Fixtures.entregaFinalizada(estabelecimento, lote, LocalDate.of(2026, 11, 9).atTime(21, 0));
        operador = Fixtures.usuario(estabelecimento, PerfilUsuario.OPERADOR);
    }

    @Test
    @DisplayName("Deve ajustar o repasse e gravar a trilha com valor anterior, novo, autor e motivo")
    void deveAjustarERegistrarTrilha() {
        when(ajusteRepository.save(any(EntregaAjusteValor.class))).then(returnsFirstArg());

        EntregaAjusteValor ajuste = ajusteRepasse.ajustar(entrega, operador, reais("12.00"), "  Distância real maior  ");

        assertEquals(reais("9.00"), ajuste.getValorAnterior());
        assertEquals(reais("12.00"), ajuste.getValorNovo());
        assertEquals("Distância real maior", ajuste.getMotivo());
        assertSame(operador, ajuste.getUsuario());
        assertNotNull(ajuste.getAjustadoEm());
        assertEquals(reais("12.00"), entrega.getValorRepasse());
        verify(entregaRepository).save(entrega);
        verify(encerramentoLote).atualizarTotal(entrega.getLote());
    }

    @Test
    @DisplayName("Deve exigir o motivo do ajuste")
    void deveExigirMotivo() {
        assertThrows(AjusteValorNaoPermitidoException.class, () ->
                ajusteRepasse.ajustar(entrega, operador, reais("12.00"), " "));
        verifyNoInteractions(ajusteRepository, entregaRepository);
    }

    @Test
    @DisplayName("Deve recusar valor negativo ou igual ao atual")
    void deveRecusarValorInvalido() {
        assertThrows(AjusteValorNaoPermitidoException.class, () ->
                ajusteRepasse.ajustar(entrega, operador, reais("-1"), "motivo"));
        assertThrows(AjusteValorNaoPermitidoException.class, () ->
                ajusteRepasse.ajustar(entrega, operador, reais("9.0"), "motivo"));
        verify(ajusteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve propagar a recusa da política de correção sem alterar a entrega")
    void devePropagarRecusaDaPolitica() {
        doThrow(new CaixaFechadoException("ajustar")).when(politicaCorrecao).exigirAjustePermitido(entrega);

        assertThrows(CaixaFechadoException.class, () ->
                ajusteRepasse.ajustar(entrega, operador, reais("12.00"), "motivo"));

        assertEquals(reais("9.00"), entrega.getValorRepasse());
        verifyNoInteractions(ajusteRepository, entregaRepository);
    }

    @Test
    @DisplayName("Deve registrar valor anterior zero quando a entrega não tinha repasse")
    void deveTratarRepasseNulo() {
        entrega.setValorRepasse(null);
        when(ajusteRepository.save(any(EntregaAjusteValor.class))).then(returnsFirstArg());

        EntregaAjusteValor ajuste = ajusteRepasse.ajustar(entrega, operador, reais("15.00"), "fora de área");

        assertEquals(reais("0"), ajuste.getValorAnterior());
        assertEquals(StatusEntrega.ENTREGUE, entrega.getStatus());
    }
}
