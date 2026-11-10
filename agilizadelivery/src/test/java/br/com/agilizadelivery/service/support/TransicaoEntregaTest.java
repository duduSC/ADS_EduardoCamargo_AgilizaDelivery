package br.com.agilizadelivery.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.EntregaStatusHistorico;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.EntregaStatusHistoricoRepository;
import br.com.agilizadelivery.service.Fixtures;

@ExtendWith(MockitoExtension.class)
class TransicaoEntregaTest {

    @Mock
    private EntregaStatusHistoricoRepository historicoRepository;

    @Captor
    private ArgumentCaptor<EntregaStatusHistorico> historico;

    private TransicaoEntrega transicao;
    private Estabelecimento estabelecimento;
    private Usuario entregador;

    @BeforeEach
    void setUp() {
        transicao = new TransicaoEntrega(historicoRepository);
        estabelecimento = Fixtures.estabelecimento();
        entregador = Fixtures.usuario(estabelecimento, PerfilUsuario.ENTREGADOR);
    }

    @Test
    @DisplayName("Deve mudar o status e gravar o histórico com o horário de origem do evento")
    void deveTransitarERegistrarHistorico() {
        Entrega entrega = Fixtures.entrega(estabelecimento, StatusEntrega.EM_ROTA);
        Coordenada onde = Fixtures.entrega(estabelecimento, StatusEntrega.EM_ROTA).getCoordenada();
        LocalDateTime offline = LocalDateTime.of(2026, 11, 9, 20, 15);

        transicao.transitar(entrega, StatusEntrega.ENTREGUE, entregador, onde, offline);

        verify(historicoRepository).save(historico.capture());
        assertEquals(StatusEntrega.ENTREGUE, entrega.getStatus());
        assertEquals(StatusEntrega.EM_ROTA, historico.getValue().getStatusAnterior());
        assertEquals(StatusEntrega.ENTREGUE, historico.getValue().getStatusNovo());
        assertSame(entregador, historico.getValue().getRegistradoPorUsuario());
        assertSame(onde, historico.getValue().getCoordenada());
        assertEquals(offline, historico.getValue().getRegistradoEm());
    }

    @Test
    @DisplayName("Deve recusar transição fora da máquina de estados sem gravar histórico")
    void deveRecusarTransicaoInvalida() {
        Entrega entrega = Fixtures.entrega(estabelecimento, StatusEntrega.AGUARDANDO_DESPACHO);

        assertThrows(TransicaoEstadoInvalidaException.class, () ->
                transicao.transitar(entrega, StatusEntrega.ENTREGUE, entregador, null, null));

        assertEquals(StatusEntrega.AGUARDANDO_DESPACHO, entrega.getStatus());
        verify(historicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve registrar a criação da entrega sem status anterior")
    void deveRegistrarCriacao() {
        Entrega entrega = Fixtures.entrega(estabelecimento, StatusEntrega.AGUARDANDO_DESPACHO);

        transicao.registrarCriacao(entrega, null);

        verify(historicoRepository).save(historico.capture());
        assertNull(historico.getValue().getStatusAnterior());
        assertEquals(StatusEntrega.AGUARDANDO_DESPACHO, historico.getValue().getStatusNovo());
    }
}
