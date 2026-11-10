package br.com.agilizadelivery.service;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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

import br.com.agilizadelivery.dto.request.DespacharLoteRequest;
import br.com.agilizadelivery.dto.response.LoteEntregaResponse;
import br.com.agilizadelivery.dto.response.ParadaRotaResponse;
import br.com.agilizadelivery.dto.response.RotaLoteResponse;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.MotivoRevisao;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.exception.AcessoNegadoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.EntregaInaptaParaLoteException;
import br.com.agilizadelivery.exception.LimiteLoteExcedidoException;
import br.com.agilizadelivery.exception.MotoboyIndisponivelException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.EntregaStatusHistoricoRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.service.impl.LoteEntregaServiceImpl;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.service.support.TransicaoEntrega;

@ExtendWith(MockitoExtension.class)
class LoteEntregaServiceTest {

    @Mock
    private LoteEntregaRepository repository;

    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private MotoboyRepository motoboyRepository;

    @Mock
    private EntregaStatusHistoricoRepository historicoRepository;

    @Mock
    private BuscadorTenant buscador;

    private LoteEntregaService service;
    private Estabelecimento estabelecimento;
    private UUID estabelecimentoId;
    private Motoboy motoboy;
    private Usuario operador;
    private Entrega a;
    private Entrega b;
    private Entrega c;

    @BeforeEach
    void setUp() {
        service = new LoteEntregaServiceImpl(repository, entregaRepository, motoboyRepository,
                buscador, new TransicaoEntrega(historicoRepository));
        estabelecimento = Fixtures.estabelecimento();
        estabelecimentoId = estabelecimento.getId();
        motoboy = Fixtures.motoboy(estabelecimento);
        operador = Fixtures.usuario(estabelecimento, PerfilUsuario.OPERADOR);
        a = entrega("A", "9.00");
        b = entrega("B", "12.00");
        c = entrega("C", "9.00");
        lenient().when(repository.save(any(LoteEntrega.class))).then(returnsFirstArg());
    }

    private Entrega entrega(String cliente, String repasse) {
        Entrega entrega = Fixtures.entrega(estabelecimento, StatusEntrega.AGUARDANDO_DESPACHO);
        entrega.setNomeCliente(cliente);
        entrega.setValorRepasse(reais(repasse));
        return entrega;
    }

    private void prepararDespacho(List<Entrega> encontradas) {
        when(buscador.estabelecimentoAtivo(estabelecimentoId)).thenReturn(estabelecimento);
        when(buscador.usuarioComPerfil(estabelecimentoId, operador.getId(),
                PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE)).thenReturn(operador);
        lenient().when(buscador.motoboy(estabelecimentoId, motoboy.getId())).thenReturn(motoboy);
        lenient().when(entregaRepository.findByEstabelecimentoIdAndIdIn(any(), any())).thenReturn(encontradas);
    }

    private DespacharLoteRequest rota(Entrega... paradas) {
        return new DespacharLoteRequest(motoboy.getId(), List.of(paradas).stream().map(Entrega::getId).toList());
    }

    /** Lote em andamento com as entregas B, C e A, nessa ordem de rota. */
    private LoteEntrega loteComRotaBCA(StatusLote status, StatusEntrega statusEntregas) {
        LoteEntrega lote = Fixtures.lote(estabelecimento, motoboy, status);
        short ordem = 1;
        for (Entrega entrega : List.of(b, c, a)) {
            entrega.setLote(lote);
            entrega.setStatus(statusEntregas);
            entrega.setOrdemRota(ordem++);
        }
        when(repository.findByIdAndEstabelecimentoId(lote.getId(), estabelecimentoId)).thenReturn(Optional.of(lote));
        lenient().when(entregaRepository.findByLoteIdOrderByOrdemRotaAsc(lote.getId())).thenReturn(List.of(b, c, a));
        return lote;
    }

    @Test
    @DisplayName("Deve despachar na ordem de paradas do operador (B, C, A) sem alterar o repasse de cada entrega")
    void deveDespacharNaOrdemDoOperador() {
        prepararDespacho(List.of(a, b, c));

        LoteEntregaResponse lote = service.despachar(estabelecimentoId, operador.getId(), rota(b, c, a));

        assertEquals((short) 1, b.getOrdemRota());
        assertEquals((short) 2, c.getOrdemRota());
        assertEquals((short) 3, a.getOrdemRota());

        assertEquals(StatusLote.DESPACHADO, lote.status());
        assertEquals((short) 3, lote.qtdEntregas());
        assertEquals(reais("30.00"), lote.valorTotalRepasse());
        assertEquals(reais("12.00"), b.getValorRepasse());
        List.of(a, b, c).forEach(entrega -> {
            assertEquals(StatusEntrega.DESPACHADA, entrega.getStatus());
            assertNotNull(entrega.getDespachadaEm());
        });
        assertEquals(StatusDisponibilidadeMotoboy.EM_ROTA, motoboy.getStatusDisponibilidade());
        verify(motoboyRepository).save(motoboy);
    }

    @Test
    @DisplayName("Deve recusar seleção acima do limite do lote (UC01 E2)")
    void deveRecusarLimiteExcedido() {
        prepararDespacho(List.of());
        estabelecimento.setMaxPedidosPorLote((short) 2);

        assertThrows(LimiteLoteExcedidoException.class, () ->
                service.despachar(estabelecimentoId, operador.getId(), rota(a, b, c)));
    }

    @Test
    @DisplayName("Deve recusar entregador OFFLINE ou BLOQUEADO (UC01 E1, HU02)")
    void deveRecusarMotoboyIndisponivel() {
        prepararDespacho(List.of(a));
        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.OFFLINE);
        assertThrows(MotoboyIndisponivelException.class, () ->
                service.despachar(estabelecimentoId, operador.getId(), rota(a)));

        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.ONLINE);
        motoboy.setStatusCadastro(StatusCadastroMotoboy.BLOQUEADO);
        assertThrows(MotoboyIndisponivelException.class, () ->
                service.despachar(estabelecimentoId, operador.getId(), rota(a)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve recusar entrega já despachada ou pendente de revisão")
    void deveRecusarEntregaInapta() {
        prepararDespacho(List.of(a));
        a.setStatus(StatusEntrega.DESPACHADA);
        assertThrows(EntregaInaptaParaLoteException.class, () ->
                service.despachar(estabelecimentoId, operador.getId(), rota(a)));

        a.setStatus(StatusEntrega.AGUARDANDO_DESPACHO);
        a.setPendenteRevisao(true);
        a.setMotivoRevisao(MotivoRevisao.SEM_COORDENADA);
        assertThrows(EntregaInaptaParaLoteException.class, () ->
                service.despachar(estabelecimentoId, operador.getId(), rota(a)));
    }

    @Test
    @DisplayName("Deve recusar entrega repetida na rota ou de outra loja")
    void deveRecusarRotaInvalida() {
        prepararDespacho(List.of(a));
        assertThrows(RegraDeNegocioException.class, () ->
                service.despachar(estabelecimentoId, operador.getId(), rota(a, a)));
        assertThrows(EntidadeNaoEncontradaException.class, () ->
                service.despachar(estabelecimentoId, operador.getId(), rota(a, b)));
    }

    @Test
    @DisplayName("Deve iniciar a rota levando lote e entregas a EM_ROTA")
    void deveIniciarRota() {
        LoteEntrega lote = loteComRotaBCA(StatusLote.DESPACHADO, StatusEntrega.DESPACHADA);

        LoteEntregaResponse emRota = service.iniciarRota(estabelecimentoId, lote.getId(), motoboy.getId());

        assertEquals(StatusLote.EM_ROTA, emRota.status());
        List.of(a, b, c).forEach(entrega -> assertEquals(StatusEntrega.EM_ROTA, entrega.getStatus()));
    }

    @Test
    @DisplayName("Deve recusar início de rota por outro entregador ou de lote já em rota")
    void deveValidarInicioDeRota() {
        LoteEntrega lote = loteComRotaBCA(StatusLote.DESPACHADO, StatusEntrega.DESPACHADA);
        assertThrows(AcessoNegadoException.class, () ->
                service.iniciarRota(estabelecimentoId, lote.getId(), UUID.randomUUID()));

        lote.setStatus(StatusLote.EM_ROTA);
        assertThrows(TransicaoEstadoInvalidaException.class, () ->
                service.iniciarRota(estabelecimentoId, lote.getId(), motoboy.getId()));
    }

    @Test
    @DisplayName("Deve devolver o lote: entregas voltam a aguardar e o entregador a ONLINE (UC01 A4)")
    void deveDevolverDespacho() {
        LoteEntrega lote = loteComRotaBCA(StatusLote.DESPACHADO, StatusEntrega.DESPACHADA);
        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.EM_ROTA);
        when(buscador.usuarioComPerfil(estabelecimentoId, operador.getId(),
                PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE)).thenReturn(operador);

        LoteEntregaResponse devolvido = service.devolverDespacho(estabelecimentoId, lote.getId(), operador.getId());

        assertEquals(StatusLote.CANCELADO, devolvido.status());
        List.of(a, b, c).forEach(entrega -> {
            assertEquals(StatusEntrega.AGUARDANDO_DESPACHO, entrega.getStatus());
            assertNull(entrega.getLote());
            assertNull(entrega.getOrdemRota());
        });
        assertEquals(StatusDisponibilidadeMotoboy.ONLINE, motoboy.getStatusDisponibilidade());
    }

    @Test
    @DisplayName("Não deve devolver lote que já saiu da loja")
    void naoDeveDevolverLoteEmRota() {
        LoteEntrega lote = loteComRotaBCA(StatusLote.EM_ROTA, StatusEntrega.EM_ROTA);
        when(buscador.usuarioComPerfil(estabelecimentoId, operador.getId(),
                PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE)).thenReturn(operador);

        assertThrows(TransicaoEstadoInvalidaException.class, () ->
                service.devolverDespacho(estabelecimentoId, lote.getId(), operador.getId()));
    }

    @Test
    @DisplayName("Deve expor a rota principal na ordem das paradas")
    void deveBuscarRota() {
        LoteEntrega lote = loteComRotaBCA(StatusLote.EM_ROTA, StatusEntrega.EM_ROTA);

        RotaLoteResponse rota = service.buscarRota(estabelecimentoId, lote.getId());

        assertEquals(List.of("B", "C", "A"), rota.paradas().stream().map(ParadaRotaResponse::nomeCliente).toList());
        assertEquals(List.of((short) 1, (short) 2, (short) 3),
                rota.paradas().stream().map(ParadaRotaResponse::ordem).toList());
        assertNotNull(rota.origem());
    }

    @Test
    @DisplayName("Deve consultar lotes da loja")
    void deveConsultar() {
        LoteEntrega lote = loteComRotaBCA(StatusLote.EM_ROTA, StatusEntrega.EM_ROTA);
        when(repository.findByEstabelecimentoIdAndStatus(estabelecimentoId, StatusLote.EM_ROTA)).thenReturn(List.of(lote));
        when(repository.findByEstabelecimentoIdAndMotoboyIdAndStatus(estabelecimentoId, motoboy.getId(), StatusLote.EM_ROTA))
                .thenReturn(List.of(lote));

        assertEquals(lote.getId(), service.buscarPorId(estabelecimentoId, lote.getId()).id());
        assertEquals(1, service.listarPorEstabelecimentoEStatus(estabelecimentoId, StatusLote.EM_ROTA).size());
        assertEquals(1, service.listarPorMotoboy(estabelecimentoId, motoboy.getId(), StatusLote.EM_ROTA).size());
    }
}
