package br.com.agilizadelivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.EntregaStatusHistorico;
import br.com.agilizadelivery.entity.model.ItemPedido;
import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.repository.EntregaStatusHistoricoRepository;
import br.com.agilizadelivery.repository.ItemPedidoRepository;
import br.com.agilizadelivery.service.impl.EntregaStatusHistoricoServiceImpl;
import br.com.agilizadelivery.service.impl.ItemPedidoServiceImpl;

/** Servicos apenas de consulta, sempre filtrados pela loja. */
@ExtendWith(MockitoExtension.class)
class ConsultasServiceTest {

    @Mock
    private ItemPedidoRepository itemPedidoRepository;

    @Mock
    private EntregaStatusHistoricoRepository historicoRepository;

    @Test
    @DisplayName("Deve listar os itens do pedido para conferência da sacola (UC02)")
    void deveListarItensDoPedido() {
        UUID estabelecimentoId = UUID.randomUUID();
        PedidoExterno pedido = new PedidoExterno();
        pedido.setId(UUID.randomUUID());
        ItemPedido item = new ItemPedido();
        item.setId(1L);
        item.setDescricao("Pizza");
        item.setQuantidade((short) 2);
        pedido.adicionarItem(item);
        when(itemPedidoRepository.findByPedidoExternoEstabelecimentoIdAndPedidoExternoId(
                estabelecimentoId, pedido.getId())).thenReturn(List.of(item));

        ItemPedidoService service = new ItemPedidoServiceImpl(itemPedidoRepository);

        assertEquals("Pizza", service.listarPorPedido(estabelecimentoId, pedido.getId()).getFirst().descricao());
    }

    @Test
    @DisplayName("Deve listar o histórico de status da entrega em ordem cronológica")
    void deveListarHistorico() {
        Entrega entrega = Fixtures.entrega(Fixtures.estabelecimento(), StatusEntrega.ENTREGUE);
        EntregaStatusHistorico historico = new EntregaStatusHistorico();
        historico.setId(1L);
        historico.setEntrega(entrega);
        historico.setStatusAnterior(StatusEntrega.EM_ROTA);
        historico.setStatusNovo(StatusEntrega.ENTREGUE);
        UUID estabelecimentoId = entrega.getEstabelecimento().getId();
        when(historicoRepository.findByEntregaEstabelecimentoIdAndEntregaIdOrderByRegistradoEmAsc(
                estabelecimentoId, entrega.getId())).thenReturn(List.of(historico));

        EntregaStatusHistoricoService service = new EntregaStatusHistoricoServiceImpl(historicoRepository);

        assertEquals(StatusEntrega.ENTREGUE,
                service.listarPorEntrega(estabelecimentoId, entrega.getId()).getFirst().statusNovo());
    }
}
