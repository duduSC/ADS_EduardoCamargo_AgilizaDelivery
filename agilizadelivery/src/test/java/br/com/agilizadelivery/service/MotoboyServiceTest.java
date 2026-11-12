package br.com.agilizadelivery.service;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.dto.request.AlterarDisponibilidadeMotoboyRequest;
import br.com.agilizadelivery.dto.request.CriarMotoboyRequest;
import br.com.agilizadelivery.dto.response.MotoboyResponse;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.exception.CpfJaCadastradoException;
import br.com.agilizadelivery.exception.EmailJaCadastradoException;
import br.com.agilizadelivery.exception.EstabelecimentoInativoException;
import br.com.agilizadelivery.exception.MotoboyIndisponivelException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.service.impl.MotoboyServiceImpl;
import br.com.agilizadelivery.service.seguranca.ProtetorCpf;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.service.support.CadastroUsuario;

@ExtendWith(MockitoExtension.class)
class MotoboyServiceTest {

    @Mock
    private MotoboyRepository repository;

    @Mock
    private BuscadorTenant buscador;

    @Mock
    private CadastroUsuario cadastroUsuario;

    @Mock
    private ProtetorCpf protetorCpf;

    @Mock
    private br.com.agilizadelivery.websocket.PublicadorTempoReal publicador;

    private MotoboyService service;
    private Estabelecimento estabelecimento;
    private Motoboy motoboy;

    @BeforeEach
    void setUp() {
        service = new MotoboyServiceImpl(repository, buscador, cadastroUsuario, protetorCpf, publicador);
        estabelecimento = Fixtures.estabelecimento();
        motoboy = Fixtures.motoboy(estabelecimento);
    }

    private CriarMotoboyRequest requisicao() {
        return new CriarMotoboyRequest("João", "joao@forno.com", "senha-segura", "123.456.789-09",
                "11999990000", "ABC1D23");
    }

    private void motoboyEncontrado() {
        when(buscador.motoboy(estabelecimento.getId(), motoboy.getId())).thenReturn(motoboy);
    }

    @Test
    @DisplayName("Deve cadastrar o entregador gerando usuário ENTREGADOR e CPF cifrado (HU09, RNF02)")
    void deveCriarMotoboy() {
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        when(protetorCpf.hash("123.456.789-09")).thenReturn("indice-cego");
        when(repository.existsByCpfHash("indice-cego")).thenReturn(false);
        when(protetorCpf.cifrar("123.456.789-09")).thenReturn("cifrado");
        Usuario usuario = Fixtures.usuario(estabelecimento, PerfilUsuario.ENTREGADOR);
        usuario.setNome("João");
        when(cadastroUsuario.criar(estabelecimento, "João", "joao@forno.com", "senha-segura",
                PerfilUsuario.ENTREGADOR)).thenReturn(usuario);
        when(repository.save(any(Motoboy.class))).then(returnsFirstArg());

        MotoboyResponse criado = service.criar(estabelecimento.getId(), requisicao());

        ArgumentCaptor<Motoboy> salvo = ArgumentCaptor.forClass(Motoboy.class);
        verify(repository).save(salvo.capture());
        assertEquals("cifrado", salvo.getValue().getCpfCriptografado());
        assertEquals("indice-cego", salvo.getValue().getCpfHash());
        assertEquals(PerfilUsuario.ENTREGADOR, salvo.getValue().getUsuario().getPerfil());
        assertEquals(estabelecimento, salvo.getValue().getUsuario().getEstabelecimento());
        assertEquals("João", criado.nome());
        assertEquals(StatusDisponibilidadeMotoboy.OFFLINE, criado.statusDisponibilidade());
    }

    @Test
    @DisplayName("Deve recusar CPF já cadastrado (UC05 E2)")
    void deveRecusarCpfDuplicado() {
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        when(protetorCpf.hash("123.456.789-09")).thenReturn("indice-cego");
        when(repository.existsByCpfHash("indice-cego")).thenReturn(true);

        assertThrows(CpfJaCadastradoException.class, () -> service.criar(estabelecimento.getId(), requisicao()));
        verifyNoInteractions(cadastroUsuario);
    }

    @Test
    @DisplayName("Deve recusar e-mail já cadastrado")
    void deveRecusarEmailDuplicado() {
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        when(protetorCpf.hash("123.456.789-09")).thenReturn("indice-cego");
        when(repository.existsByCpfHash("indice-cego")).thenReturn(false);
        when(cadastroUsuario.criar(any(), any(), any(), any(), any()))
                .thenThrow(new EmailJaCadastradoException("joao@forno.com"));

        assertThrows(EmailJaCadastradoException.class, () -> service.criar(estabelecimento.getId(), requisicao()));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve bloquear o entregador e forçá-lo a OFFLINE")
    void deveBloquear() {
        motoboyEncontrado();
        when(repository.save(motoboy)).thenReturn(motoboy);

        MotoboyResponse bloqueado = service.alterarStatusCadastro(
                estabelecimento.getId(), motoboy.getId(), StatusCadastroMotoboy.BLOQUEADO);

        assertEquals(StatusCadastroMotoboy.BLOQUEADO, bloqueado.statusCadastro());
        assertEquals(StatusDisponibilidadeMotoboy.OFFLINE, bloqueado.statusDisponibilidade());
    }

    @Test
    @DisplayName("Deve recusar bloqueio de entregador em rota")
    void deveRecusarBloqueioEmRota() {
        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.EM_ROTA);
        motoboyEncontrado();

        assertThrows(RegraDeNegocioException.class, () -> service.alterarStatusCadastro(
                estabelecimento.getId(), motoboy.getId(), StatusCadastroMotoboy.BLOQUEADO));
    }

    @Test
    @DisplayName("Deve permitir ficar ONLINE com cadastro ativo e loja ativa")
    void deveFicarOnline() {
        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.OFFLINE);
        motoboyEncontrado();
        when(repository.save(motoboy)).thenReturn(motoboy);

        MotoboyResponse online = service.alterarDisponibilidade(estabelecimento.getId(), motoboy.getId(),
                new AlterarDisponibilidadeMotoboyRequest(StatusDisponibilidadeMotoboy.ONLINE));

        assertEquals(StatusDisponibilidadeMotoboy.ONLINE, online.statusDisponibilidade());
    }

    @Test
    @DisplayName("Deve impedir entregador BLOQUEADO de ficar ONLINE (HU09)")
    void deveImpedirBloqueadoOnline() {
        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.OFFLINE);
        motoboy.setStatusCadastro(StatusCadastroMotoboy.BLOQUEADO);
        motoboyEncontrado();

        assertThrows(MotoboyIndisponivelException.class, () -> service.alterarDisponibilidade(
                estabelecimento.getId(), motoboy.getId(),
                new AlterarDisponibilidadeMotoboyRequest(StatusDisponibilidadeMotoboy.ONLINE)));
    }

    @Test
    @DisplayName("Deve impedir ficar ONLINE em loja inativa (HU12)")
    void deveImpedirOnlineEmLojaInativa() {
        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.OFFLINE);
        estabelecimento.setStatus(StatusEstabelecimento.INATIVO);
        motoboyEncontrado();

        assertThrows(EstabelecimentoInativoException.class, () -> service.alterarDisponibilidade(
                estabelecimento.getId(), motoboy.getId(),
                new AlterarDisponibilidadeMotoboyRequest(StatusDisponibilidadeMotoboy.ONLINE)));
    }

    @Test
    @DisplayName("Deve reservar EM_ROTA ao ciclo do lote")
    void deveRecusarEmRotaManual() {
        motoboyEncontrado();

        assertThrows(TransicaoEstadoInvalidaException.class, () -> service.alterarDisponibilidade(
                estabelecimento.getId(), motoboy.getId(),
                new AlterarDisponibilidadeMotoboyRequest(StatusDisponibilidadeMotoboy.EM_ROTA)));

        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.EM_ROTA);
        assertThrows(TransicaoEstadoInvalidaException.class, () -> service.alterarDisponibilidade(
                estabelecimento.getId(), motoboy.getId(),
                new AlterarDisponibilidadeMotoboyRequest(StatusDisponibilidadeMotoboy.OFFLINE)));
    }

    @Test
    @DisplayName("Deve registrar a última posição apenas em ONLINE ou EM_ROTA (RNF07)")
    void deveAtualizarPosicao() {
        motoboyEncontrado();
        when(repository.save(motoboy)).thenReturn(motoboy);

        MotoboyResponse atualizado = service.atualizarPosicao(
                estabelecimento.getId(), motoboy.getId(), reais("-23.55"), reais("-46.63"));

        assertEquals(reais("-23.55"), atualizado.ultimaPosicao().latitude());
        assertNotNull(atualizado.ultimaPosicaoEm());

        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.OFFLINE);
        assertThrows(RegraDeNegocioException.class, () -> service.atualizarPosicao(
                estabelecimento.getId(), motoboy.getId(), reais("-23.55"), reais("-46.63")));
    }

    @Test
    @DisplayName("Deve recusar coordenada fora das faixas válidas")
    void deveRecusarCoordenadaInvalida() {
        motoboyEncontrado();

        assertThrows(RegraDeNegocioException.class, () -> service.atualizarPosicao(
                estabelecimento.getId(), motoboy.getId(), reais("91"), reais("0")));
    }

    @Test
    @DisplayName("Deve consultar e listar os entregadores da loja")
    void deveConsultar() {
        motoboyEncontrado();
        when(repository.findByUsuarioEstabelecimentoId(estabelecimento.getId())).thenReturn(List.of(motoboy));
        when(repository.findByUsuarioEstabelecimentoIdAndStatusCadastro(
                estabelecimento.getId(), StatusCadastroMotoboy.ATIVO)).thenReturn(List.of(motoboy));

        assertEquals(motoboy.getId(), service.buscarPorId(estabelecimento.getId(), motoboy.getId()).id());
        assertEquals(1, service.listarPorEstabelecimento(estabelecimento.getId()).size());
        assertEquals(1, service.listarPorStatusCadastro(estabelecimento.getId(), StatusCadastroMotoboy.ATIVO).size());
    }
}
