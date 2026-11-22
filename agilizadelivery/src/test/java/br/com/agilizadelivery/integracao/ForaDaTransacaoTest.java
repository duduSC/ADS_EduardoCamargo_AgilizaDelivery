package br.com.agilizadelivery.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.zaxxer.hikari.HikariDataSource;

import br.com.agilizadelivery.controller.CenarioWeb;
import br.com.agilizadelivery.dto.request.CriarEntregaAvulsaRequest;
import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.integracao.pedidos.PedidoRecebido;
import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.service.EntregaService;
import br.com.agilizadelivery.service.rota.ServicoDeGeocodificacao;
import br.com.agilizadelivery.service.rota.ServicoDeRotas;
import br.com.agilizadelivery.service.support.ImportacaoPedido;

/**
 * O Google leva ate 5 s por chamada: ela nao pode prender transacao, conexao
 * do pool (10) nem a trava da loja (T014, D).
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:mapas;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ForaDaTransacaoTest {

    @MockitoBean
    private ServicoDeGeocodificacao geocodificacao;
    @MockitoBean
    private ServicoDeRotas rotas;

    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private EntregaService entregaService;
    @Autowired
    private ImportacaoPedido importacao;
    @Autowired
    private EntregaRepository entregaRepository;
    @Autowired
    private ConfiguracaoIntegracaoRepository configuracaoRepository;
    @Autowired
    private DataSource dataSource;

    private final List<String> chamadas = new ArrayList<>();
    private Estabelecimento loja;

    @BeforeEach
    void setUp() throws Exception {
        loja = cenario.estabelecimento();
        var pool = dataSource.unwrap(HikariDataSource.class);
        when(geocodificacao.geocodificar(any())).thenAnswer(chamada -> {
            anotar("geocodificacao", pool);
            return Optional.of(new Coordenada(new BigDecimal("-23.56"), new BigDecimal("-46.65")));
        });
        when(rotas.distanciaKm(any(), any())).thenAnswer(chamada -> {
            anotar("rotas", pool);
            return Optional.of(new BigDecimal("3.20"));
        });
    }

    private void anotar(String servico, HikariDataSource pool) {
        chamadas.add(servico
                + " transacao=" + TransactionSynchronizationManager.isActualTransactionActive()
                + " conexoes=" + pool.getHikariPoolMXBean().getActiveConnections());
    }

    @Test
    @DisplayName("Entrega avulsa consulta o mapa sem transação nem conexão")
    void entregaAvulsa() {
        var criada = entregaService.criarAvulsa(loja.getId(), new CriarEntregaAvulsaRequest("Cliente", null,
                "Rua A, 10", null, null, null, null, null, null, null, true, null, null));

        assertThat(chamadas).containsExactly(
                "geocodificacao transacao=false conexoes=0", "rotas transacao=false conexoes=0");
        Entrega gravada = entregaRepository.findById(criada.id()).orElseThrow();
        assertThat(gravada.getDistanciaKm()).isEqualByComparingTo("3.20");
        assertThat(gravada.getValorRepasse()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("Ajuste do pino consulta o mapa sem segurar a trava da loja")
    void ajusteDoPino() {
        UUID operador = cenario.usuario(loja, PerfilUsuario.OPERADOR).getId();
        UUID entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO).getId();

        entregaService.ajustarCoordenada(loja.getId(), entrega,
                new BigDecimal("-23.5700000"), new BigDecimal("-46.6600000"), operador);

        assertThat(chamadas).containsExactly("rotas transacao=false conexoes=0");
        Entrega gravada = entregaRepository.findById(entrega).orElseThrow();
        assertThat(gravada.getCoordenada().getLatitude()).isEqualByComparingTo("-23.57");
        assertThat(gravada.getDistanciaKm()).isEqualByComparingTo("3.20");
    }

    @Test
    @DisplayName("Importação consulta o mapa sem transação e continua idempotente")
    void importacao() {
        ConfiguracaoIntegracao configuracao = new ConfiguracaoIntegracao();
        configuracao.setEstabelecimento(loja);
        configuracao.setProvedor("SIMULADOR");
        configuracao.setMerchantId("mapas-" + loja.getId());
        configuracao.setAtivo(true);
        configuracaoRepository.save(configuracao);
        PedidoRecebido pedido = new PedidoRecebido("mapas-" + UUID.randomUUID(), "1", "PLACED", "Cliente", null,
                "Rua B, 20", null, null, null, null, null, null, null, BigDecimal.TEN, null, true, null, null,
                List.of(), "{}");

        assertThat(importacao.importar(configuracao, pedido)).isPresent();
        assertThat(chamadas).containsExactly(
                "geocodificacao transacao=false conexoes=0", "rotas transacao=false conexoes=0");

        assertThat(importacao.importar(configuracao, pedido)).isEmpty();
        assertThat(chamadas).hasSize(2);
    }
}
