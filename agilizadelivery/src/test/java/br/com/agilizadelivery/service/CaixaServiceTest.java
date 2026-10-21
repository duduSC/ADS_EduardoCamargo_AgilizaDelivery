package br.com.agilizadelivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.exception.CaixaJaAbertoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.EstabelecimentoInativoException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class CaixaServiceTest {

    @Mock
    private CaixaRepository caixaRepository;

    @Mock
    private EstabelecimentoRepository estabelecimentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CaixaService caixaService;

    private UUID estabelecimentoId;
    private UUID caixaId;
    private UUID usuarioId;
    private Estabelecimento estabelecimento;
    private Caixa caixa;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        estabelecimentoId = UUID.randomUUID();
        caixaId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();

        estabelecimento = new Estabelecimento();
        estabelecimento.setId(estabelecimentoId);
        estabelecimento.setNomeFantasia("Pizzaria Forno Nobre");
        estabelecimento.setStatus(StatusEstabelecimento.ATIVO);

        caixa = new Caixa();
        caixa.setId(caixaId);
        caixa.setEstabelecimento(estabelecimento);
        caixa.setDataReferencia(LocalDate.now());
        caixa.setStatus(StatusCaixa.ABERTO);

        usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setNome("Operador Carlos");
    }

    @Test
    @DisplayName("Deve abrir o caixa com sucesso quando estabelecimento estiver ativo e data disponivel")
    void deveAbrirCaixaComSucesso() {
        LocalDate hoje = LocalDate.now();
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(estabelecimento));
        when(caixaRepository.existsByEstabelecimentoIdAndDataReferencia(estabelecimentoId, hoje)).thenReturn(false);
        when(caixaRepository.save(any(Caixa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Caixa resultado = caixaService.abrirCaixa(estabelecimentoId, hoje);

        assertNotNull(resultado);
        assertEquals(StatusCaixa.ABERTO, resultado.getStatus());
        assertEquals(hoje, resultado.getDataReferencia());
        verify(caixaRepository).save(any(Caixa.class));
    }

    @Test
    @DisplayName("Deve lançar EstabelecimentoInativoException ao tentar abrir caixa para loja inativa")
    void deveLancarExcecaoQuandoEstabelecimentoInativo() {
        LocalDate hoje = LocalDate.now();
        estabelecimento.setStatus(StatusEstabelecimento.INATIVO);
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(estabelecimento));

        assertThrows(EstabelecimentoInativoException.class, () ->
                caixaService.abrirCaixa(estabelecimentoId, hoje)
        );

        verify(caixaRepository, never()).save(any(Caixa.class));
    }

    @Test
    @DisplayName("Deve lançar CaixaJaAbertoException quando o caixa do dia já existe")
    void deveLancarExcecaoQuandoCaixaJaAberto() {
        LocalDate hoje = LocalDate.now();
        when(estabelecimentoRepository.findById(estabelecimentoId)).thenReturn(Optional.of(estabelecimento));
        when(caixaRepository.existsByEstabelecimentoIdAndDataReferencia(estabelecimentoId, hoje)).thenReturn(true);

        assertThrows(CaixaJaAbertoException.class, () ->
                caixaService.abrirCaixa(estabelecimentoId, hoje)
        );

        verify(caixaRepository, never()).save(any(Caixa.class));
    }

    @Test
    @DisplayName("Deve fechar caixa aberto com sucesso")
    void deveFecharCaixaComSucesso() {
        when(caixaRepository.findById(caixaId)).thenReturn(Optional.of(caixa));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(caixaRepository.save(any(Caixa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Caixa fechado = caixaService.fecharCaixa(caixaId, usuarioId);

        assertEquals(StatusCaixa.FECHADO, fechado.getStatus());
        assertNotNull(fechado.getFechadoEm());
        assertEquals(usuario, fechado.getUsuarioFechamento());
    }

    @Test
    @DisplayName("Deve lançar CaixaFechadoException ao tentar fechar um caixa que já está fechado")
    void deveLancarExcecaoAoFecharCaixaJaFechado() {
        caixa.setStatus(StatusCaixa.FECHADO);
        when(caixaRepository.findById(caixaId)).thenReturn(Optional.of(caixa));

        assertThrows(CaixaFechadoException.class, () ->
                caixaService.fecharCaixa(caixaId, usuarioId)
        );
    }

    @Test
    @DisplayName("Deve reabrir caixa fechado com justificativa")
    void deveReabrirCaixaFechadoComSucesso() {
        caixa.setStatus(StatusCaixa.FECHADO);
        when(caixaRepository.findById(caixaId)).thenReturn(Optional.of(caixa));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(caixaRepository.save(any(Caixa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Caixa reaberto = caixaService.reabrirCaixa(caixaId, usuarioId, "Correção de entrega duplicada");

        assertEquals(StatusCaixa.REABERTO, reaberto.getStatus());
        assertEquals("Correção de entrega duplicada", reaberto.getMotivoReabertura());
        assertNotNull(reaberto.getReabertoEm());
    }

    @Test
    @DisplayName("Deve lançar TransicaoEstadoInvalidaException ao tentar reabrir caixa que não está fechado")
    void deveLancarExcecaoAoReabrirCaixaNaoFechado() {
        caixa.setStatus(StatusCaixa.ABERTO);
        when(caixaRepository.findById(caixaId)).thenReturn(Optional.of(caixa));

        assertThrows(TransicaoEstadoInvalidaException.class, () ->
                caixaService.reabrirCaixa(caixaId, usuarioId, "Tentativa indevida")
        );
    }

    @Test
    @DisplayName("Deve lançar RegraDeNegocioException ao tentar reabrir caixa sem informar motivo")
    void deveLancarExcecaoAoReabrirCaixaSemMotivo() {
        caixa.setStatus(StatusCaixa.FECHADO);
        when(caixaRepository.findById(caixaId)).thenReturn(Optional.of(caixa));

        assertThrows(RegraDeNegocioException.class, () ->
                caixaService.reabrirCaixa(caixaId, usuarioId, "   ")
        );
    }

    @Test
    @DisplayName("Deve lançar EntidadeNaoEncontradaException ao buscar id inexistente")
    void deveLancarExcecaoQuandoCaixaNaoEncontrado() {
        when(caixaRepository.findById(caixaId)).thenReturn(Optional.empty());

        assertThrows(EntidadeNaoEncontradaException.class, () ->
                caixaService.buscarPorId(caixaId)
        );
    }
}
