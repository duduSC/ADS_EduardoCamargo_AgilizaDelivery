package br.com.agilizadelivery.integracao.pedidos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.FalhaIntegracao;
import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.EtapaFalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.FormaPagamento;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusFalhaIntegracao;
import br.com.agilizadelivery.controller.CenarioWeb;
import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.FalhaIntegracaoRepository;
import br.com.agilizadelivery.repository.PedidoExternoRepository;
import br.com.agilizadelivery.service.support.ImportacaoPedido;
import br.com.agilizadelivery.service.support.Provedores;
import tools.jackson.databind.ObjectMapper;

/**
 * Ciclo do worker de integracao (T011, DVP 1.5.6). O agendamento fica
 * desligado nos testes: o ciclo e montado e chamado direto, com recuo
 * zero para as repeticoes do RNF04 nao atrasarem a suite. Banco H2 proprio.
 */
@SpringBootTest(properties = {
        "agiliza.maps.api-key=",
        "spring.datasource.url=jdbc:h2:mem:ciclo-t011;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class CicloIntegracaoTest {

    private static final IntegracaoPedidosProperties PARAMETROS = new IntegracaoPedidosProperties(
            new IntegracaoPedidosProperties.Worker(false, Duration.ofSeconds(30), Duration.ZERO, 5), null);

    @Autowired
    private CenarioWeb cenario;

    @Autowired
    private ConfiguracaoIntegracaoRepository configuracaoRepository;

    @Autowired
    private FalhaIntegracaoRepository falhaRepository;

    @Autowired
    private PedidoExternoRepository pedidoRepository;

    @Autowired
    private EntregaRepository entregaRepository;

    @Autowired
    private ImportacaoPedido importacao;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transacao;

    private Estabelecimento loja;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
    }

    private ConfiguracaoIntegracao configuracao(boolean ativa) {
        ConfiguracaoIntegracao configuracao = new ConfiguracaoIntegracao();
        configuracao.setEstabelecimento(loja);
        configuracao.setProvedor(Provedores.SIMULADOR);
        configuracao.setMerchantId("merchant-" + UUID.randomUUID());
        configuracao.setAtivo(ativa);
        return configuracaoRepository.save(configuracao);
    }

    private CicloIntegracao cicloCom(ProvedorDePedidos provedor) {
        return new CicloIntegracao(List.of(provedor), configuracaoRepository, falhaRepository, importacao, PARAMETROS);
    }

    private CicloIntegracao cicloComSimulador() {
        return cicloCom(new SimuladorAdapter(entregaRepository, PARAMETROS, objectMapper, new Random(11)));
    }

    private List<Entrega> aguardando() {
        return entregaRepository.findByEstabelecimentoIdAndStatusOrderByCriadoEmAsc(
                loja.getId(), StatusEntrega.AGUARDANDO_DESPACHO);
    }

    private List<FalhaIntegracao> falhas() {
        return falhaRepository.findByConfiguracaoIntegracaoEstabelecimentoIdAndStatus(
                loja.getId(), StatusFalhaIntegracao.PENDENTE);
    }

    @Test
    @DisplayName("O ciclo cria entregas AGUARDANDO_DESPACHO com pedido, itens, distancia e repasse")
    void cicloCriaEntregas() {
        ConfiguracaoIntegracao configuracao = configuracao(true);
        CicloIntegracao ciclo = cicloComSimulador();
        for (int i = 0; i < 10 && aguardando().isEmpty(); i++) {
            ciclo.executar();
        }

        List<Entrega> entregas = aguardando();
        assertThat(entregas).isNotEmpty().allSatisfy(entrega -> {
            assertThat(entrega.getPendenteRevisao()).isFalse();
            assertThat(entrega.getDistanciaKm()).isNotNull();
            assertThat(entrega.getValorRepasse()).isNotNull();
        });
        transacao.executeWithoutResult(status -> {
            PedidoExterno pedido = entregaRepository.findById(entregas.getFirst().getId()).orElseThrow()
                    .getPedidoExterno();
            assertThat(pedido.getProvedor()).isEqualTo(Provedores.SIMULADOR);
            assertThat(pedido.getProcessadoEm()).isNotNull();
            assertThat(pedido.getPayloadJson()).contains(pedido.getIdExterno());
            assertThat(pedido.getItens()).hasSizeBetween(1, 4);
        });
        assertThat(configuracaoRepository.findById(configuracao.getId()).orElseThrow().getUltimoPollingEm())
                .isNotNull();
    }

    @Test
    @DisplayName("O mesmo id externo nao e importado duas vezes")
    void idempotencia() {
        ConfiguracaoIntegracao configuracao = configuracao(true);
        String idExterno = "FIXO-" + UUID.randomUUID();
        CicloIntegracao ciclo = cicloCom(provedorFixo(configuracao, merchant ->
                List.of(pedido(idExterno, "Cliente Repetido"), pedido(idExterno, "Cliente Repetido"))));

        ciclo.executar();
        ciclo.executar();

        assertThat(pedidoRepository.existsByProvedorAndIdExterno(Provedores.SIMULADOR, idExterno)).isTrue();
        assertThat(aguardando()).hasSize(1);
        assertThat(falhas()).isEmpty();
    }

    @Test
    @DisplayName("Falha num pedido grava FALHA_INTEGRACAO PENDENTE e nao interrompe os outros")
    void falhaNumPedido() {
        ConfiguracaoIntegracao configuracao = configuracao(true);
        String ruim = "RUIM-" + UUID.randomUUID();
        CicloIntegracao ciclo = cicloCom(provedorFixo(configuracao, merchant -> List.of(
                pedido("OK1-" + UUID.randomUUID(), "Cliente Um"),
                pedido(ruim, null),
                pedido("OK2-" + UUID.randomUUID(), "Cliente Dois"))));

        ciclo.executar();

        assertThat(aguardando()).extracting(Entrega::getNomeCliente)
                .containsExactlyInAnyOrder("Cliente Um", "Cliente Dois");
        assertThat(falhas()).singleElement().satisfies(falha -> {
            assertThat(falha.getIdExterno()).isEqualTo(ruim);
            assertThat(falha.getEtapa()).isEqualTo(EtapaFalhaIntegracao.PERSISTENCIA);
            assertThat(falha.getTentativas()).isEqualTo((short) 5);
            assertThat(falha.getPayloadJson()).contains(ruim);
            assertThat(falha.getMensagemErro()).isNotBlank();
        });
        assertThat(pedidoRepository.existsByProvedorAndIdExterno(Provedores.SIMULADOR, ruim)).isFalse();
    }

    @Test
    @DisplayName("Provedor fora do ar: FALHA_INTEGRACAO na CONSULTA, sem atualizar o ultimo polling")
    void falhaNaConsulta() {
        ConfiguracaoIntegracao configuracao = configuracao(true);
        int[] chamadas = {0};
        CicloIntegracao ciclo = cicloCom(provedorFixo(configuracao, merchant -> {
            chamadas[0]++;
            throw new IllegalStateException("provedor indisponivel");
        }));

        ciclo.executar();

        assertThat(chamadas[0]).isEqualTo(5);
        assertThat(falhas()).singleElement()
                .satisfies(falha -> assertThat(falha.getEtapa()).isEqualTo(EtapaFalhaIntegracao.CONSULTA));
        assertThat(configuracaoRepository.findById(configuracao.getId()).orElseThrow().getUltimoPollingEm())
                .isNull();
    }

    @Test
    @DisplayName("Loja INATIVA nao recebe pedidos do worker (HU12)")
    void lojaInativa() {
        loja = cenario.estabelecimento(
                br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento.INATIVO, (short) 5);
        configuracao(true);
        CicloIntegracao ciclo = cicloComSimulador();

        for (int i = 0; i < 10; i++) {
            ciclo.executar();
        }

        assertThat(aguardando()).isEmpty();
    }

    @Test
    @DisplayName("Configuracao inativa nao gera nada")
    void configuracaoInativa() {
        configuracao(false);
        CicloIntegracao ciclo = cicloComSimulador();
        for (int i = 0; i < 10; i++) {
            ciclo.executar();
        }
        assertThat(aguardando()).isEmpty();
    }

    @Test
    @DisplayName("Com 15 entregas aguardando despacho, o simulador para de gerar")
    void limiteDeQuinze() {
        for (int i = 0; i < 15; i++) {
            cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
        }
        configuracao(true);
        CicloIntegracao ciclo = cicloComSimulador();
        for (int i = 0; i < 10; i++) {
            ciclo.executar();
        }
        assertThat(aguardando()).hasSize(15).allSatisfy(entrega -> assertThat(entrega.getPedidoExterno()).isNull());
    }

    /** Provedor de teste que so responde pela propria configuracao. */
    private static ProvedorDePedidos provedorFixo(
            ConfiguracaoIntegracao propria, Function<String, List<PedidoRecebido>> resposta) {
        return new ProvedorDePedidos() {
            @Override
            public String provedor() {
                return Provedores.SIMULADOR;
            }

            @Override
            public List<PedidoRecebido> buscarNovosPedidos(ConfiguracaoIntegracao configuracao) {
                if (!propria.getId().equals(configuracao.getId())) {
                    return List.of();
                }
                return new ArrayList<>(resposta.apply(configuracao.getMerchantId()));
            }

            @Override
            public void confirmarRecebimento(ConfiguracaoIntegracao configuracao, String idExterno) {
            }
        };
    }

    private static PedidoRecebido pedido(String idExterno, String nomeCliente) {
        return new PedidoRecebido(idExterno, "1", "PLACED", nomeCliente, "54999990000",
                "Rua Morom, 100 - Centro, Passo Fundo/RS", "Centro", "99010-031", null, null,
                new Coordenada(new BigDecimal("-23.5600000"), new BigDecimal("-46.6400000")),
                new BigDecimal("50.00"), new BigDecimal("7.00"), new BigDecimal("57.00"), FormaPagamento.PIX, true,
                null, null, List.of(new PedidoRecebido.Item("Pizza", (short) 1, new BigDecimal("50.00"), null)),
                "{\"id\":\"" + idExterno + "\"}");
    }
}
