package br.com.agilizadelivery.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.agilizadelivery.controller.CenarioWeb;
import br.com.agilizadelivery.dto.request.DespacharLoteRequest;
import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.integracao.pedidos.PedidoRecebido;
import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.service.support.ImportacaoPedido;

/**
 * A trava por loja convive com o que roda o tempo todo: posicao (20 s por
 * entregador), polling do lote (10 s), painel e simulador (30 s). Nenhum
 * espera pela trava, nenhum a atrasa e nenhum cai no 409 da espera de 10 s.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:convivencia;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class TravaDaLojaConvivenciaTest {

    private static final long SEM_ESPERA_SEGUNDOS = 3;

    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private PlatformTransactionManager gerenciador;
    @Autowired
    private EstabelecimentoRepository estabelecimentoRepository;
    @Autowired
    private ConfiguracaoIntegracaoRepository configuracaoRepository;
    @Autowired
    private EntregadorService entregadorService;
    @Autowired
    private EntregaService entregaService;
    @Autowired
    private LoteEntregaService loteService;
    @Autowired
    private ImportacaoPedido importacao;

    private ExecutorService executor;
    private Estabelecimento loja;
    private UUID lojaId;
    private UUID operador;
    private Motoboy motoboy;
    private ConfiguracaoIntegracao configuracao;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(8);
        loja = cenario.estabelecimento();
        lojaId = loja.getId();
        operador = cenario.usuario(loja, PerfilUsuario.OPERADOR).getId();
        motoboy = cenario.motoboy(loja);
        ConfiguracaoIntegracao nova = new ConfiguracaoIntegracao();
        nova.setEstabelecimento(loja);
        nova.setProvedor("SIMULADOR");
        nova.setMerchantId("convivencia-" + lojaId);
        nova.setAtivo(true);
        configuracao = configuracaoRepository.save(nova);
    }

    @AfterEach
    void encerrar() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Com a trava presa, posição, polling, painel e simulador não esperam")
    void ninguemEsperaPelaTrava() throws Exception {
        comTransacaoAberta(() -> estabelecimentoRepository.bloquearPorId(lojaId), () -> {
            semEspera(this::posicao);
            semEspera(this::polling);
            semEspera(this::painel);
            semEspera(this::simulador);
            semEspera(() -> entregadorService.acerto(lojaId, motoboy.getUsuario().getId(), null, null));
        });
    }

    @Test
    @DisplayName("Posição ainda não confirmada não segura a trava")
    void posicaoNaoSeguraATrava() throws Exception {
        comTransacaoAberta(this::posicao, () -> semEspera(this::travar));
    }

    @Test
    @DisplayName("Simulador no meio da importação não segura a trava nem a posição")
    void simuladorNaoSeguraATrava() throws Exception {
        comTransacaoAberta(this::simulador, () -> {
            semEspera(this::travar);
            semEspera(this::posicao);
        });
    }

    @Test
    @DisplayName("Tudo junto, como na apresentação: nenhum erro e tudo em menos de 3 s")
    void tudoJunto() throws Exception {
        List<Throwable> erros = new CopyOnWriteArrayList<>();
        List<Long> demoras = new CopyOnWriteArrayList<>();
        List<Runnable> papeis = List.of(this::posicao, this::posicao, this::polling, this::polling,
                this::painel, this::simulador, this::despacharEDevolver);
        List<Future<?>> rodando = new ArrayList<>();
        for (Runnable papel : papeis) {
            rodando.add(executor.submit(() -> {
                for (int i = 0; i < 20; i++) {
                    long inicio = System.nanoTime();
                    try {
                        papel.run();
                    } catch (RuntimeException e) {
                        erros.add(e);
                    }
                    demoras.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
                }
            }));
        }
        for (Future<?> papel : rodando) {
            papel.get(60, TimeUnit.SECONDS);
        }

        assertThat(erros).isEmpty();
        assertThat(demoras).allMatch(ms -> ms < TimeUnit.SECONDS.toMillis(SEM_ESPERA_SEGUNDOS));
    }

    private interface Verificacao {
        void executar() throws Exception;
    }

    private void comTransacaoAberta(Supplier<?> dentro, Verificacao enquantoAberta) throws Exception {
        CountDownLatch aberta = new CountDownLatch(1);
        CountDownLatch confirmar = new CountDownLatch(1);
        Future<?> transacao = executor.submit(() -> new TransactionTemplate(gerenciador).executeWithoutResult(s -> {
            dentro.get();
            estabelecimentoRepository.flush(); // os locks de linha ja tomados, sem commit
            aberta.countDown();
            try {
                confirmar.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        try {
            assertThat(aberta.await(10, TimeUnit.SECONDS)).isTrue();
            enquantoAberta.executar();
        } finally {
            confirmar.countDown();
            transacao.get(10, TimeUnit.SECONDS);
        }
    }

    private void semEspera(Supplier<?> operacao) throws Exception {
        // falha com TimeoutException se ficar esperando
        executor.submit(() -> operacao.get()).get(SEM_ESPERA_SEGUNDOS, TimeUnit.SECONDS);
    }

    private Object posicao() {
        return entregadorService.atualizarPosicao(lojaId, motoboy.getUsuario().getId(),
                new BigDecimal("-23.5600000"), new BigDecimal("-46.6500000"));
    }

    private Object polling() {
        return entregadorService.buscarLoteAtivo(lojaId, motoboy.getUsuario().getId());
    }

    private Object painel() {
        entregaService.listarPendentesDeDespacho(lojaId);
        return loteService.listarPorEstabelecimentoEStatus(lojaId,
                StatusLote.DESPACHADO);
    }

    private Object simulador() {
        return importacao.importar(configuracao, new PedidoRecebido("convivencia-" + UUID.randomUUID(), "1",
                "PLACED", "Cliente", null, "Rua B, 20", null, null, null, null, loja.getCoordenada(), null, null,
                BigDecimal.TEN, null, true, null, null, List.of(), "{}"));
    }

    private Object travar() {
        return new TransactionTemplate(gerenciador).execute(s -> estabelecimentoRepository.bloquearPorId(lojaId));
    }

    private Object despacharEDevolver() {
        UUID entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO).getId();
        var lote = loteService.despachar(lojaId, operador, new DespacharLoteRequest(motoboy.getId(), List.of(entrega)));
        return loteService.devolverDespacho(lojaId, lote.id(), operador);
    }
}
