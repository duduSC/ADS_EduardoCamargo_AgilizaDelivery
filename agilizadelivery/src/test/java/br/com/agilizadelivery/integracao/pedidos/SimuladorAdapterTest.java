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
                new IntegracaoPedidosProperties(null, null), json, new Random(7), false);
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
    @DisplayName("Dados coerentes: 1 a 4 itens, totais, pagamento e troco")
    void dadosCoerentes() {
        for (PedidoRecebido pedido : gerarMuitos()) {
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

    @Test
    @DisplayName("A lista tem ao menos 25 enderecos reais, sem repeticao, com CEP de Passo Fundo e bairro mapeado")
    void listaDeEnderecos() {
        assertThat(EnderecosPassoFundo.ENDERECOS).hasSizeGreaterThanOrEqualTo(25)
                .extracting(EnderecosPassoFundo.Endereco::linha).doesNotHaveDuplicates();
        assertThat(EnderecosPassoFundo.ENDERECOS).allSatisfy(endereco -> {
            assertThat(endereco.cep()).matches("990\\d{2}-\\d{3}");
            assertThat(endereco.numero()).isPositive();
            assertThat(EnderecosPassoFundo.CENTRO_DO_BAIRRO).containsKey(endereco.bairro());
        });
    }

    @Test
    @DisplayName("Sem chave: endereco da lista e coordenada a ate 300 m do centro do bairro")
    void semChaveCoordenadaDoBairro() {
        List<PedidoRecebido> pedidos = gerarMuitos();
        assertThat(pedidos).isNotEmpty().allSatisfy(pedido -> {
            EnderecosPassoFundo.Endereco endereco = daLista(pedido);
            assertThat(pedido.bairro()).isEqualTo(endereco.bairro());
            assertThat(pedido.cep()).isEqualTo(endereco.cep());
            assertThat(pedido.coordenada()).isNotNull();
            assertThat(distanciaKm(EnderecosPassoFundo.CENTRO_DO_BAIRRO.get(endereco.bairro()), pedido.coordenada()))
                    .isLessThanOrEqualTo(0.301);
        });
        assertThat(pedidos.stream().map(PedidoRecebido::enderecoCompleto).distinct().count()).isGreaterThan(10);
    }

    @Test
    @DisplayName("Com chave: o pedido vai sem coordenada, para o backend geocodificar o endereco")
    void comChaveSemCoordenada() {
        simulador = new SimuladorAdapter(entregaRepository,
                new IntegracaoPedidosProperties(null, null), json, new Random(7), true);
        List<PedidoRecebido> pedidos = gerarMuitos();
        assertThat(pedidos).isNotEmpty().allSatisfy(pedido -> {
            daLista(pedido);
            assertThat(pedido.coordenada()).isNull();
            JsonNode entrega = json.readTree(pedido.payloadJson()).get("deliveryAddress");
            assertThat(entrega.get("latitude").isNull()).isTrue();
            assertThat(entrega.get("postalCode").asString()).isEqualTo(pedido.cep());
        });
    }

    private static EnderecosPassoFundo.Endereco daLista(PedidoRecebido pedido) {
        return EnderecosPassoFundo.ENDERECOS.stream()
                .filter(endereco -> endereco.linha().equals(pedido.enderecoCompleto()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("fora da lista: " + pedido.enderecoCompleto()));
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
