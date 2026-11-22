package br.com.agilizadelivery.service;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import br.com.agilizadelivery.dto.response.PedidoExternoResponse;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.ItemPedido;
import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.PedidoJaImportadoException;
import br.com.agilizadelivery.exception.ProvedorDesconhecidoException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.handler.CodigoErro;
import br.com.agilizadelivery.repository.PedidoExternoRepository;
import br.com.agilizadelivery.service.impl.PedidoExternoServiceImpl;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.service.support.Provedores;
import br.com.agilizadelivery.tempo.HorarioLocal;

@ExtendWith(MockitoExtension.class)
class PedidoExternoServiceTest {

    @Mock
    private PedidoExternoRepository repository;

    @Mock
    private BuscadorTenant buscador;

    private PedidoExternoService service;
    private Estabelecimento estabelecimento;
    private PedidoExterno pedido;

    @BeforeEach
    void setUp() {
        service = new PedidoExternoServiceImpl(repository, buscador);
        estabelecimento = Fixtures.estabelecimento();
        pedido = new PedidoExterno();
        pedido.setId(UUID.randomUUID());
        pedido.setProvedor(Provedores.IFOOD);
        pedido.setIdExterno("ifood-123");
        pedido.setNomeCliente("Cliente");
        pedido.setValorTotal(reais("59.90"));
        pedido.setPayloadJson("{}");
        ItemPedido item = new ItemPedido();
        item.setDescricao("Pizza");
        item.setQuantidade((short) 1);
        pedido.adicionarItem(item);
    }

    @Test
    @DisplayName("Deve importar o pedido vinculando loja e itens")
    void deveImportar() {
        pedido.getItens().getFirst().setPedidoExterno(null);
        when(repository.existsByProvedorAndIdExterno(Provedores.IFOOD, "ifood-123")).thenReturn(false);
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        when(repository.save(any(PedidoExterno.class))).then(returnsFirstArg());

        PedidoExternoResponse importado = service.importar(estabelecimento.getId(), pedido);

        assertEquals(estabelecimento.getId(), importado.estabelecimentoId());
        assertEquals(1, importado.itens().size());
        assertSame(pedido, pedido.getItens().getFirst().getPedidoExterno());
    }

    @Test
    @DisplayName("Deve garantir a idempotência da importação (HU03)")
    void deveRecusarPedidoDuplicado() {
        when(repository.existsByProvedorAndIdExterno(Provedores.IFOOD, "ifood-123")).thenReturn(true);

        assertThrows(PedidoJaImportadoException.class, () -> service.importar(estabelecimento.getId(), pedido));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve marcar o pedido como processado uma única vez")
    void deveMarcarProcessado() {
        pedido.setEstabelecimento(estabelecimento);
        when(repository.findByIdAndEstabelecimentoId(pedido.getId(), estabelecimento.getId()))
                .thenReturn(Optional.of(pedido));
        when(repository.save(pedido)).thenReturn(pedido);

        assertNotNull(service.marcarProcessado(estabelecimento.getId(), pedido.getId()).processadoEm());

        pedido.setProcessadoEm(HorarioLocal.agora());
        assertThrows(RegraDeNegocioException.class, () ->
                service.marcarProcessado(estabelecimento.getId(), pedido.getId()));
    }

    @Test
    @DisplayName("Deve consultar pedidos da loja e isolar os de outra loja")
    void deveConsultar() {
        pedido.setEstabelecimento(estabelecimento);
        when(repository.findByIdAndEstabelecimentoId(pedido.getId(), estabelecimento.getId()))
                .thenReturn(Optional.of(pedido));
        when(repository.findByEstabelecimentoIdAndProcessadoEmIsNull(estabelecimento.getId()))
                .thenReturn(List.of(pedido));
        when(repository.existsByProvedorAndIdExterno(Provedores.SIMULADOR, "sim-1")).thenReturn(true);

        assertEquals(pedido.getId(), service.buscarPorId(estabelecimento.getId(), pedido.getId()).id());
        assertEquals(1, service.listarNaoProcessados(estabelecimento.getId()).size());
        assertTrue(service.jaImportado(Provedores.SIMULADOR, "sim-1"));
        assertThrows(EntidadeNaoEncontradaException.class, () ->
                service.buscarPorId(UUID.randomUUID(), pedido.getId()));
    }

    @Test
    @DisplayName("Deve normalizar o provedor antes da chave de idempotência")
    void deveNormalizarProvedorNaIdempotencia() {
        pedido.setProvedor(" ifood ");
        when(repository.existsByProvedorAndIdExterno(Provedores.IFOOD, "ifood-123")).thenReturn(true);

        assertThrows(PedidoJaImportadoException.class, () -> service.importar(estabelecimento.getId(), pedido));
        assertEquals(Provedores.IFOOD, pedido.getProvedor());
        assertTrue(service.jaImportado("Ifood", "ifood-123"));
    }

    @Test
    @DisplayName("Deve recusar provedor desconhecido com 400 DADOS_INVALIDOS")
    void deveRecusarProvedorDesconhecido() {
        pedido.setProvedor("RAPPI");

        ProvedorDesconhecidoException erro = assertThrows(ProvedorDesconhecidoException.class,
                () -> service.importar(estabelecimento.getId(), pedido));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusHttp());
        assertEquals(CodigoErro.DADOS_INVALIDOS, erro.getCodigoErro());
        assertThrows(ProvedorDesconhecidoException.class, () -> service.jaImportado("RAPPI", "x"));
        verify(repository, never()).save(any());
        verify(repository, never()).existsByProvedorAndIdExterno(any(), any());
    }
}
