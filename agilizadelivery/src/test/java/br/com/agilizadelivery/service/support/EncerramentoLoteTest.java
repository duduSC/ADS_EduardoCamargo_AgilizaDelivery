package br.com.agilizadelivery.service.support;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.service.Fixtures;

@ExtendWith(MockitoExtension.class)
class EncerramentoLoteTest {

    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private LoteEntregaRepository loteRepository;

    @Mock
    private MotoboyRepository motoboyRepository;

    private EncerramentoLote encerramento;
    private Estabelecimento estabelecimento;
    private Motoboy motoboy;

    @BeforeEach
    void setUp() {
        encerramento = new EncerramentoLote(entregaRepository, loteRepository, motoboyRepository);
        estabelecimento = Fixtures.estabelecimento();
        motoboy = Fixtures.motoboy(estabelecimento);
        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.EM_ROTA);
    }

    private Entrega entrega(StatusEntrega status, String valor) {
        Entrega entrega = Fixtures.entrega(estabelecimento, status);
        entrega.setValorRepasse(reais(valor));
        return entrega;
    }

    @Test
    @DisplayName("Deve concluir o lote em rota quando todas as entregas estão finalizadas")
    void deveConcluirLote() {
        LoteEntrega lote = Fixtures.lote(estabelecimento, motoboy, StatusLote.EM_ROTA);
        when(entregaRepository.findByLoteIdOrderByOrdemRotaAsc(lote.getId())).thenReturn(List.of(
                entrega(StatusEntrega.ENTREGUE, "9.00"),
                entrega(StatusEntrega.ENTREGUE, "12.00"),
                entrega(StatusEntrega.FALHA, "9.00")));

        encerramento.avaliar(lote);

        assertEquals(StatusLote.CONCLUIDO, lote.getStatus());
        assertNotNull(lote.getConcluidoEm());
        assertEquals(reais("30.00"), lote.getValorTotalRepasse());
        assertEquals(StatusDisponibilidadeMotoboy.ONLINE, motoboy.getStatusDisponibilidade());
        verify(motoboyRepository).save(motoboy);
    }

    @Test
    @DisplayName("Deve manter o lote em rota enquanto houver entrega pendente")
    void deveManterLoteComEntregaPendente() {
        LoteEntrega lote = Fixtures.lote(estabelecimento, motoboy, StatusLote.EM_ROTA);
        when(entregaRepository.findByLoteIdOrderByOrdemRotaAsc(lote.getId())).thenReturn(List.of(
                entrega(StatusEntrega.ENTREGUE, "9.00"),
                entrega(StatusEntrega.EM_ROTA, "12.00")));

        encerramento.avaliar(lote);

        assertEquals(StatusLote.EM_ROTA, lote.getStatus());
        assertNull(lote.getConcluidoEm());
        assertEquals(reais("21.00"), lote.getValorTotalRepasse());
        verify(loteRepository).save(lote);
        verify(motoboyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve cancelar o lote despachado quando todas as entregas forem canceladas antes da saída")
    void deveCancelarLoteDespachado() {
        LoteEntrega lote = Fixtures.lote(estabelecimento, motoboy, StatusLote.DESPACHADO);
        when(entregaRepository.findByLoteIdOrderByOrdemRotaAsc(lote.getId()))
                .thenReturn(List.of(entrega(StatusEntrega.CANCELADA, "0")));

        encerramento.avaliar(lote);

        assertEquals(StatusLote.CANCELADO, lote.getStatus());
        assertEquals(StatusDisponibilidadeMotoboy.ONLINE, motoboy.getStatusDisponibilidade());
    }

    @Test
    @DisplayName("Não deve alterar lote ausente ou já encerrado")
    void naoDeveAlterarLoteEncerrado() {
        encerramento.avaliar(null);
        encerramento.avaliar(Fixtures.lote(estabelecimento, motoboy, StatusLote.CONCLUIDO));

        verifyNoInteractions(entregaRepository, loteRepository, motoboyRepository);
    }

    @Test
    @DisplayName("Deve recalcular o total do lote após um ajuste, mesmo concluído")
    void deveAtualizarTotal() {
        LoteEntrega lote = Fixtures.lote(estabelecimento, motoboy, StatusLote.CONCLUIDO);
        when(entregaRepository.findByLoteIdOrderByOrdemRotaAsc(lote.getId())).thenReturn(List.of(
                entrega(StatusEntrega.ENTREGUE, "10.00"),
                entrega(StatusEntrega.ENTREGUE, "12.00")));

        encerramento.atualizarTotal(lote);

        assertEquals(reais("22.00"), lote.getValorTotalRepasse());
        verify(loteRepository).save(lote);
    }
}
