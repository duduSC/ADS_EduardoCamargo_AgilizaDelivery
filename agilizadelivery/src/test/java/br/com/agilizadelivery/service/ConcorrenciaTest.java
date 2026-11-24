package br.com.agilizadelivery.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.agilizadelivery.controller.CenarioWeb;
import br.com.agilizadelivery.dto.request.AbrirCaixaRequest;
import br.com.agilizadelivery.dto.request.AjustarRepasseRequest;
import br.com.agilizadelivery.dto.request.AlterarDisponibilidadeMotoboyRequest;
import br.com.agilizadelivery.dto.request.AlterarStatusEntregaRequest;
import br.com.agilizadelivery.dto.request.DespacharLoteRequest;
import br.com.agilizadelivery.dto.request.EditarEstabelecimentoRequest;
import br.com.agilizadelivery.dto.request.GerarReciboRequest;
import br.com.agilizadelivery.dto.request.SalvarDistanciaPrecoRequest;
import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
import br.com.agilizadelivery.exception.AjusteValorNaoPermitidoException;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.exception.CaixaJaAbertoException;
import br.com.agilizadelivery.exception.EntregaInaptaParaLoteException;
import br.com.agilizadelivery.exception.MotoboyIndisponivelException;
import br.com.agilizadelivery.exception.ReciboInalteravelException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.repository.DistanciaPrecoRepository;
import br.com.agilizadelivery.repository.EntregaAjusteValorRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.EntregaStatusHistoricoRepository;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.repository.ReciboRepository;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Requisicoes simultaneas (T014, B): duas transacoes de verdade. A primeira
 * executa e so confirma depois de a segunda ter tido tempo de executar; o
 * resultado tem de ser o mesmo de uma depois da outra.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:concorrencia;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class ConcorrenciaTest {

    private static final long TEMPO_DA_SEGUNDA_MS = 700;

    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private PlatformTransactionManager gerenciadorDeTransacao;
    @Autowired
    private LoteEntregaService loteService;
    @Autowired
    private EntregaService entregaService;
    @Autowired
    private EntregadorService entregadorService;
    @Autowired
    private EntregaAjusteValorService ajusteService;
    @Autowired
    private ReciboService reciboService;
    @Autowired
    private CaixaService caixaService;
    @Autowired
    private MotoboyService motoboyService;
    @Autowired
    private EstabelecimentoService estabelecimentoService;
    @Autowired
    private DistanciaPrecoService faixaService;
    @Autowired
    private EntregaRepository entregaRepository;
    @Autowired
    private EntregaStatusHistoricoRepository historicoRepository;
    @Autowired
    private EntregaAjusteValorRepository ajusteRepository;
    @Autowired
    private LoteEntregaRepository loteRepository;
    @Autowired
    private MotoboyRepository motoboyRepository;
    @Autowired
    private ReciboRepository reciboRepository;
    @Autowired
    private CaixaRepository caixaRepository;
    @Autowired
    private EstabelecimentoRepository estabelecimentoRepository;
    @Autowired
    private DistanciaPrecoRepository faixaRepository;

    private ExecutorService executor;
    private TransactionTemplate transacao;
    private Estabelecimento loja;
    private UUID lojaId;
    private UUID operador;
    private UUID administrador;
    private Motoboy motoboy;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        transacao = new TransactionTemplate(gerenciadorDeTransacao);
        loja = cenario.estabelecimento();
        lojaId = loja.getId();
        operador = cenario.usuario(loja, PerfilUsuario.OPERADOR).getId();
        administrador = cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE).getId();
        motoboy = cenario.motoboy(loja);
    }

    @AfterEach
    void encerrar() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("A mesma entrega não entra em dois lotes")
    void mesmaEntregaEmDoisLotes() throws Exception {
        UUID entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO).getId();
        Motoboy outro = cenario.motoboy(loja);

        Throwable erro = correr(() -> despachar(motoboy, entrega), () -> despachar(outro, entrega));

        assertThat(erro).isInstanceOf(EntregaInaptaParaLoteException.class);
        assertThat(lotes(StatusLote.DESPACHADO)).hasSize(1);
    }

    @Test
    @DisplayName("O mesmo entregador não recebe dois lotes")
    void doisLotesParaOMesmoEntregador() throws Exception {
        UUID primeira = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO).getId();
        UUID segunda = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO).getId();

        Throwable erro = correr(() -> despachar(motoboy, primeira), () -> despachar(motoboy, segunda));

        assertThat(erro).isInstanceOf(MotoboyIndisponivelException.class);
        assertThat(lotes(StatusLote.DESPACHADO)).hasSize(1);
    }

    @Test
    @DisplayName("Ficar OFFLINE junto com o despacho não deixa lote com entregador OFFLINE")
    void offlineDuranteDespacho() throws Exception {
        UUID entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO).getId();
        UUID usuario = motoboy.getUsuario().getId();

        Throwable erro = correr(() -> despachar(motoboy, entrega),
                () -> entregadorService.alterarDisponibilidade(lojaId, usuario,
                        new AlterarDisponibilidadeMotoboyRequest(StatusDisponibilidadeMotoboy.OFFLINE)));

        assertThat(erro).isInstanceOf(TransicaoEstadoInvalidaException.class);
        assertThat(motoboyNoBanco().getStatusDisponibilidade()).isEqualTo(StatusDisponibilidadeMotoboy.EM_ROTA);
    }

    @Test
    @DisplayName("Gerar o mesmo recibo duas vezes não cobra duas vezes")
    void reciboDobrado() throws Exception {
        cenario.caixa(loja);
        entregaFinalizada();

        Throwable erro = correr(this::gerarRecibo, this::gerarRecibo);

        assertThat(erro).isInstanceOf(RegraDeNegocioException.class);
        assertThat(reciboRepository.findByEstabelecimentoIdAndMotoboyId(lojaId, motoboy.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Recibo não nasce num caixa que está fechando")
    void reciboDuranteFechamento() throws Exception {
        UUID caixa = cenario.caixa(loja).getId();
        entregaFinalizada();

        Throwable erro = correr(() -> fechar(caixa), this::gerarRecibo);

        assertThat(erro).isInstanceOf(CaixaFechadoException.class);
        assertThat(reciboRepository.findByEstabelecimentoIdAndCaixaId(lojaId, caixa)).isEmpty();
    }

    @Test
    @DisplayName("Fechamento consolida o recibo que estava sendo gerado")
    void fechamentoDuranteRecibo() throws Exception {
        UUID caixa = cenario.caixa(loja).getId();
        entregaFinalizada();

        Throwable erro = correr(this::gerarRecibo, () -> fechar(caixa));

        assertThat(erro).isNull();
        Caixa fechado = caixaRepository.findById(caixa).orElseThrow();
        assertThat(fechado.getQtdRecibos()).isEqualTo((short) 1);
        assertThat(fechado.getValorTotal()).isEqualByComparingTo("9.00");
    }

    @Test
    @DisplayName("O caixa não fecha duas vezes")
    void fechamentoDobrado() throws Exception {
        UUID caixa = cenario.caixa(loja).getId();

        Throwable erro = correr(() -> fechar(caixa),
                () -> caixaService.fecharCaixa(lojaId, caixa, administrador));

        assertThat(erro).isInstanceOf(CaixaFechadoException.class);
        assertThat(caixaRepository.findById(caixa).orElseThrow().getFechadoEm()).isNotNull();
    }

    @Test
    @DisplayName("Abrir o caixa duas vezes responde CAIXA_JA_ABERTO")
    void aberturaDobrada() throws Exception {
        AbrirCaixaRequest hoje = new AbrirCaixaRequest(HorarioLocal.hoje());

        Throwable erro = correr(() -> caixaService.abrirCaixa(lojaId, hoje),
                () -> caixaService.abrirCaixa(lojaId, hoje));

        assertThat(erro).isInstanceOf(CaixaJaAbertoException.class);
    }

    @Test
    @DisplayName("Pagar não ressuscita um recibo que está sendo cancelado")
    void pagarDuranteCancelamento() throws Exception {
        Recibo recibo = reciboComEntrega(cenario.caixa(loja));

        Throwable erro = correr(() -> reciboService.cancelar(lojaId, recibo.getId(), operador, "Divergência"),
                () -> reciboService.marcarComoPago(lojaId, recibo.getId(), operador));

        assertThat(erro).isInstanceOf(TransicaoEstadoInvalidaException.class);
        assertThat(reciboRepository.findById(recibo.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusRecibo.CANCELADO);
    }

    @Test
    @DisplayName("Recibo não é cancelado num caixa que está fechando")
    void cancelamentoDuranteFechamento() throws Exception {
        Caixa caixa = cenario.caixa(loja);
        Recibo recibo = reciboComEntrega(caixa);

        Throwable erro = correr(() -> fechar(caixa.getId()),
                () -> reciboService.cancelar(lojaId, recibo.getId(), operador, "Divergência"));

        assertThat(erro).isInstanceOf(ReciboInalteravelException.class);
    }

    @Test
    @DisplayName("Pagamento não entra num caixa que está fechando")
    void pagamentoDuranteFechamento() throws Exception {
        Caixa caixa = cenario.caixa(loja);
        Recibo recibo = reciboComEntrega(caixa);

        Throwable erro = correr(() -> fechar(caixa.getId()),
                () -> reciboService.marcarComoPago(lojaId, recibo.getId(), operador));

        assertThat(erro).isInstanceOf(CaixaFechadoException.class);
    }

    @Test
    @DisplayName("Ajuste não solta a entrega do recibo que está sendo gerado")
    void ajusteDuranteRecibo() throws Exception {
        cenario.caixa(loja);
        UUID entrega = entregaFinalizada().getId();

        Throwable erro = correr(this::gerarRecibo, () -> ajustar(entrega));

        assertThat(erro).isInstanceOf(AjusteValorNaoPermitidoException.class);
        Entrega atual = entregaRepository.findById(entrega).orElseThrow();
        assertThat(atual.getRecibo()).isNotNull();
        assertThat(atual.getValorRepasse()).isEqualByComparingTo("9.00");
    }

    @Test
    @DisplayName("Ajuste não entra num caixa que está fechando")
    void ajusteDuranteFechamento() throws Exception {
        UUID caixa = cenario.caixa(loja).getId();
        UUID entrega = entregaFinalizada().getId();

        Throwable erro = correr(() -> fechar(caixa), () -> ajustar(entrega));

        assertThat(erro).isInstanceOf(CaixaFechadoException.class);
        assertThat(ajusteRepository.findByEntregaEstabelecimentoIdAndEntregaIdOrderByAjustadoEmAsc(lojaId, entrega))
                .isEmpty();
    }

    @Test
    @DisplayName("Contestação não entra num caixa que está fechando")
    void contestacaoDuranteFechamento() throws Exception {
        UUID caixa = cenario.caixa(loja).getId();
        UUID entrega = entregaFinalizada().getId();

        Throwable erro = correr(() -> fechar(caixa),
                () -> entregadorService.contestar(lojaId, motoboy.getUsuario().getId(), entrega));

        assertThat(erro).isInstanceOf(CaixaFechadoException.class);
        assertThat(entregaRepository.findById(entrega).orElseThrow().getPendenteRevisao()).isFalse();
    }

    @Test
    @DisplayName("Cancelar e entregar ao mesmo tempo dá um único estado final")
    void cancelarDuranteEntrega() throws Exception {
        UUID entrega = entregaNoLote(lote(StatusLote.EM_ROTA), StatusEntrega.EM_ROTA).getId();

        Throwable erro = correr(() -> entregaService.cancelar(lojaId, entrega, operador, "Cliente desistiu"),
                () -> entregar(entrega));

        assertThat(erro).isInstanceOf(TransicaoEstadoInvalidaException.class);
        assertThat(historicoRepository.findByEntregaEstabelecimentoIdAndEntregaIdOrderByRegistradoEmAsc(
                lojaId, entrega)).hasSize(1);
    }

    @Test
    @DisplayName("Duas últimas entregas finalizadas juntas encerram o lote e liberam o entregador")
    void ultimasEntregasJuntas() throws Exception {
        LoteEntrega lote = lote(StatusLote.EM_ROTA);
        UUID primeira = entregaNoLote(lote, StatusEntrega.EM_ROTA).getId();
        UUID segunda = entregaNoLote(lote, StatusEntrega.EM_ROTA).getId();

        Throwable erro = correr(() -> entregar(primeira), () -> entregar(segunda));

        assertThat(erro).isNull();
        assertThat(loteRepository.findById(lote.getId()).orElseThrow().getStatus()).isEqualTo(StatusLote.CONCLUIDO);
        assertThat(motoboyNoBanco().getStatusDisponibilidade()).isEqualTo(StatusDisponibilidadeMotoboy.ONLINE);
    }

    @Test
    @DisplayName("Devolver e sair com o lote ao mesmo tempo: só um vale")
    void devolverDuranteSaida() throws Exception {
        LoteEntrega lote = lote(StatusLote.DESPACHADO);
        UUID entrega = entregaNoLote(lote, StatusEntrega.DESPACHADA).getId();

        Throwable erro = correr(() -> loteService.devolverDespacho(lojaId, lote.getId(), operador),
                () -> entregadorService.iniciarLote(lojaId, motoboy.getUsuario().getId(), lote.getId()));

        assertThat(erro).isInstanceOf(TransicaoEstadoInvalidaException.class);
        assertThat(entregaRepository.findById(entrega).orElseThrow().getStatus())
                .isEqualTo(StatusEntrega.AGUARDANDO_DESPACHO);
    }

    @Test
    @DisplayName("Posição do GPS não desfaz um bloqueio")
    void posicaoDuranteBloqueio() throws Exception {
        Throwable erro = correr(this::enviarPosicao,
                () -> motoboyService.alterarStatusCadastro(lojaId, motoboy.getId(), StatusCadastroMotoboy.BLOQUEADO));

        assertThat(erro).isNull();
        Motoboy atual = motoboyNoBanco();
        assertThat(atual.getStatusCadastro()).isEqualTo(StatusCadastroMotoboy.BLOQUEADO);
        assertThat(atual.getStatusDisponibilidade()).isEqualTo(StatusDisponibilidadeMotoboy.OFFLINE);
        assertThat(atual.getUltimaPosicao()).isNotNull();
    }

    @Test
    @DisplayName("Posição do GPS não desfaz o EM_ROTA do despacho")
    void posicaoDuranteDespacho() throws Exception {
        UUID entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO).getId();

        Throwable erro = correr(this::enviarPosicao, () -> despachar(motoboy, entrega));

        assertThat(erro).isNull();
        assertThat(motoboyNoBanco().getStatusDisponibilidade()).isEqualTo(StatusDisponibilidadeMotoboy.EM_ROTA);
    }

    @Test
    @DisplayName("Editar parâmetros não reativa uma loja que foi inativada")
    void edicaoDuranteInativacao() throws Exception {
        Throwable erro = correr(
                () -> estabelecimentoService.editarParametros(lojaId,
                        new EditarEstabelecimentoRequest((short) 4, null, null)),
                () -> estabelecimentoService.alterarStatus(lojaId, StatusEstabelecimento.INATIVO));

        assertThat(erro).isNull();
        Estabelecimento atual = estabelecimentoRepository.findById(lojaId).orElseThrow();
        assertThat(atual.getStatus()).isEqualTo(StatusEstabelecimento.INATIVO);
        assertThat(atual.getMaxPedidosPorLote()).isEqualTo((short) 4);
    }

    @Test
    @DisplayName("Duas gravações da tabela de faixas deixam uma só tabela ativa")
    void tabelasDeFaixasSimultaneas() throws Exception {
        Throwable erro = correr(() -> salvarFaixas("9.00"), () -> salvarFaixas("10.00"));

        assertThat(erro).isNull();
        assertThat(faixaRepository.findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(lojaId))
                .singleElement()
                .satisfies(faixa -> assertThat(faixa.getValorPago()).isEqualByComparingTo("10.00"));
    }

    /** Devolve o erro da segunda operacao; a primeira tem de dar certo. */
    private Throwable correr(Runnable primeira, Runnable segunda) throws Exception {
        CountDownLatch executada = new CountDownLatch(1);
        CountDownLatch confirmar = new CountDownLatch(1);
        Future<?> umaTransacao = executor.submit(() -> transacao.executeWithoutResult(status -> {
            primeira.run();
            executada.countDown();
            aguardar(confirmar);
        }));
        if (!executada.await(10, TimeUnit.SECONDS)) {
            umaTransacao.get(1, TimeUnit.SECONDS);
        }
        Future<Throwable> outraTransacao = executor.submit(() -> {
            try {
                segunda.run();
                return null;
            } catch (RuntimeException e) {
                return e;
            }
        });
        try {
            outraTransacao.get(TEMPO_DA_SEGUNDA_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException esperandoAPrimeira) {
            // serializada: so termina depois do commit da primeira
        }
        confirmar.countDown();
        umaTransacao.get(15, TimeUnit.SECONDS);
        return outraTransacao.get(15, TimeUnit.SECONDS);
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("a segunda operação não liberou a primeira");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void despachar(Motoboy destino, UUID entrega) {
        loteService.despachar(lojaId, operador, new DespacharLoteRequest(destino.getId(), List.of(entrega)));
    }

    private void gerarRecibo() {
        reciboService.gerar(lojaId, operador, new GerarReciboRequest(motoboy.getId(),
                HorarioLocal.hoje().atStartOfDay(), HorarioLocal.hoje().atTime(23, 59, 59)));
    }

    private void fechar(UUID caixa) {
        caixaService.fecharCaixa(lojaId, caixa, operador);
    }

    private void ajustar(UUID entrega) {
        ajusteService.ajustar(lojaId, entrega, operador,
                new AjustarRepasseRequest(new BigDecimal("12.00"), "Distância conferida"));
    }

    private void entregar(UUID entrega) {
        entregadorService.alterarStatusEntrega(lojaId, motoboy.getUsuario().getId(), entrega,
                new AlterarStatusEntregaRequest(StatusEntrega.ENTREGUE, null, null, HorarioLocal.agora()));
    }

    private void enviarPosicao() {
        entregadorService.atualizarPosicao(lojaId, motoboy.getUsuario().getId(),
                new BigDecimal("-23.5600000"), new BigDecimal("-46.6500000"));
    }

    private void salvarFaixas(String valor) {
        faixaService.substituirTabela(lojaId, List.of(new SalvarDistanciaPrecoRequest(
                BigDecimal.ZERO, new BigDecimal("5.00"), new BigDecimal(valor), HorarioLocal.hoje())));
    }

    private List<LoteEntrega> lotes(StatusLote status) {
        return loteRepository.findByEstabelecimentoIdAndStatus(lojaId, status);
    }

    private Motoboy motoboyNoBanco() {
        return motoboyRepository.findById(motoboy.getId()).orElseThrow();
    }

    private LoteEntrega lote(StatusLote status) {
        motoboy.setStatusDisponibilidade(status == StatusLote.CONCLUIDO
                ? StatusDisponibilidadeMotoboy.ONLINE : StatusDisponibilidadeMotoboy.EM_ROTA);
        motoboyRepository.save(motoboy);
        Usuario despachante = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        LoteEntrega lote = new LoteEntrega();
        lote.setEstabelecimento(loja);
        lote.setMotoboy(motoboy);
        lote.setUsuarioDespachante(despachante);
        lote.setStatus(status);
        lote.setQtdEntregas((short) 2);
        lote.setDespachadoEm(HorarioLocal.agora());
        return loteRepository.save(lote);
    }

    private Entrega entregaNoLote(LoteEntrega lote, StatusEntrega status) {
        Entrega entrega = cenario.entrega(loja, status);
        entrega.setLote(lote);
        entrega.setOrdemRota((short) 1);
        return entregaRepository.save(entrega);
    }

    private Entrega entregaFinalizada() {
        Entrega entrega = entregaNoLote(lote(StatusLote.CONCLUIDO), StatusEntrega.ENTREGUE);
        LocalDateTime meioDia = HorarioLocal.hoje().atTime(12, 0);
        entrega.setFinalizadaEm(meioDia);
        return entregaRepository.save(entrega);
    }

    private Recibo reciboComEntrega(Caixa caixa) {
        Recibo recibo = cenario.recibo(loja, motoboy, caixa,
                cenario.usuario(loja, PerfilUsuario.OPERADOR));
        Entrega entrega = entregaFinalizada();
        entrega.setRecibo(recibo);
        entregaRepository.save(entrega);
        return recibo;
    }
}
