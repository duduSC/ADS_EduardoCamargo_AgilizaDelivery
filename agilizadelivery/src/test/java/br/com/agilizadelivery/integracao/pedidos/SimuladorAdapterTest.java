package br.com.agilizadelivery.integracao.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.FormaPagamento;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.service.Fixtures;
import br.com.agilizadelivery.service.support.Provedores;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Pedidos sinteticos do simulador (T011). */
class SimuladorAdapterTest {

    private static final double RAIO_TERRA_KM = 6371.0;

    private final JsonMapper json = JsonMapper.builder().build();
    private EntregaRepository entregaRepository;
    private ConfiguracaoIntegracao configuracao;
    private SimuladorAdapter simulador;

    @BeforeEach
    void setUp() {
        entregaRepository = mock(EntregaRepository.class);
        Estabelecimento loja = Fixtures.estabelecimento();
        loja.setCoordenada(new Coordenada(new BigDecimal("-28.2620000"), new BigDecimal("-52.4083000")));
        configuracao = new ConfiguracaoIntegracao();
        configuracao.setEstabelecimento(loja);
        configuracao.setProvedor(Provedores.SIMULADOR);
        configuracao.setMerchantId("demo");
        simulador = new SimuladorAdapter(entregaRepository,
                new IntegracaoPedidosProperties(null, null), json, new Random(7));
    }

    private List<PedidoRecebido> gerarMuitos() {
        when(entregaRepository.countByEstabelecimentoIdAndStatus(any(), eq(StatusEntrega.AGUARDANDO_DESPACHO)))
                .thenReturn(0L);
        List<PedidoRecebido> pedidos = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            List<PedidoRecebido> ciclo = simulador.buscarNovosPedidos(configuracao);
            assertThat(ciclo).hasSizeLessThanOrEqualTo(2);
            pedidos.addAll(ciclo);
        }
        return pedidos;
    }

    @Test
    @DisplayName("Gera de 0 a 2 pedidos por ciclo, com ids externos unicos")
    void quantidadeEIds() {
        List<PedidoRecebido> pedidos = gerarMuitos();
        assertThat(pedidos).isNotEmpty();
        assertThat(pedidos).extracting(PedidoRecebido::idExterno).doesNotHaveDuplicates();
        assertThat(pedidos).extracting(PedidoRecebido::numeroExibicao).doesNotHaveDuplicates();
        assertThat(simulador.provedor()).isEqualTo(Provedores.SIMULADOR);
    }

    @Test
    @DisplayName("Dados coerentes: raio de 6 km, 1 a 4 itens, totais, pagamento e troco")
    void dadosCoerentes() {
        for (PedidoRecebido pedido : gerarMuitos()) {
            Coordenada loja = configuracao.getEstabelecimento().getCoordenada();
            assertThat(distanciaKm(loja, pedido.coordenada())).isLessThanOrEqualTo(6.01);
            assertThat(pedido.itens()).hasSizeBetween(1, 4).allSatisfy(item -> {
                assertThat(item.descricao()).isNotBlank();
                assertThat(item.quantidade()).isPositive();
                assertThat(item.valorUnitario()).isPositive();
            });
            BigDecimal itens = pedido.itens().stream()
                    .map(item -> item.valorUnitario().multiply(BigDecimal.valueOf(item.quantidade())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(pedido.valorItens()).isEqualByComparingTo(itens);
            assertThat(pedido.valorTotal()).isEqualByComparingTo(itens.add(pedido.valorTaxaEntrega()));
            if (pedido.pagoOnline()) {
                assertThat(pedido.formaPagamento()).isIn(FormaPagamento.PIX, FormaPagamento.CREDITO);
            }
            if (pedido.trocoPara() != null) {
                assertThat(pedido.formaPagamento()).isEqualTo(FormaPagamento.DINHEIRO);
                assertThat(pedido.trocoPara()).isGreaterThan(pedido.valorTotal());
            }
            assertThat(pedido.nomeCliente()).contains(" ");
            assertThat(pedido.enderecoCompleto()).endsWith("Passo Fundo/RS");
        }
    }

    @Test
    @DisplayName("O payload e o JSON do pedido, com o id externo")
    void payloadJson() {
        PedidoRecebido pedido = gerarMuitos().getFirst();
        JsonNode payload = json.readTree(pedido.payloadJson());
        assertThat(payload.get("id").asString()).isEqualTo(pedido.idExterno());
        assertThat(payload.get("items").size()).isEqualTo(pedido.itens().size());
    }

    @Test
    @DisplayName("Com 15 entregas aguardando, nao gera; com 14, gera no maximo 1")
    void limiteDeSeguranca() {
        when(entregaRepository.countByEstabelecimentoIdAndStatus(any(), eq(StatusEntrega.AGUARDANDO_DESPACHO)))
                .thenReturn(15L);
        for (int i = 0; i < 50; i++) {
            assertThat(simulador.buscarNovosPedidos(configuracao)).isEmpty();
        }
        when(entregaRepository.countByEstabelecimentoIdAndStatus(any(), eq(StatusEntrega.AGUARDANDO_DESPACHO)))
                .thenReturn(14L);
        for (int i = 0; i < 50; i++) {
            assertThat(simulador.buscarNovosPedidos(configuracao)).hasSizeLessThanOrEqualTo(1);
        }
    }

    private static double distanciaKm(Coordenada a, Coordenada b) {
        double lat1 = Math.toRadians(a.getLatitude().doubleValue());
        double lat2 = Math.toRadians(b.getLatitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.getLongitude().doubleValue() - a.getLongitude().doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * RAIO_TERRA_KM * Math.asin(Math.sqrt(h));
    }
}
