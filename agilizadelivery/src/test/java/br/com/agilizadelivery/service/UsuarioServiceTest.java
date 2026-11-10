package br.com.agilizadelivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.agilizadelivery.dto.request.CriarUsuarioRequest;
import br.com.agilizadelivery.dto.response.UsuarioResponse;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.exception.EmailJaCadastradoException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.repository.UsuarioRepository;
import br.com.agilizadelivery.service.impl.UsuarioServiceImpl;
import br.com.agilizadelivery.service.support.BuscadorTenant;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository repository;

    @Mock
    private BuscadorTenant buscador;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioService service;
    private Estabelecimento estabelecimento;

    @BeforeEach
    void setUp() {
        service = new UsuarioServiceImpl(repository, buscador, passwordEncoder);
        estabelecimento = Fixtures.estabelecimento();
    }

    @Test
    @DisplayName("Deve criar operador com e-mail normalizado e senha em hash BCrypt (RNF02)")
    void deveCriarUsuario() {
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        when(repository.existsByLoginEmail("ana@forno.com")).thenReturn(false);
        when(passwordEncoder.encode("senha-segura")).thenReturn("$2a$10$hash");
        when(repository.save(any(Usuario.class))).then(returnsFirstArg());

        UsuarioResponse criado = service.criar(estabelecimento.getId(),
                new CriarUsuarioRequest("Ana", " Ana@Forno.com ", "senha-segura", PerfilUsuario.OPERADOR));

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(salvo.capture());
        assertEquals("$2a$10$hash", salvo.getValue().getSenhaHash());
        assertEquals("ana@forno.com", criado.loginEmail());
        assertEquals(estabelecimento.getId(), criado.estabelecimentoId());
        assertEquals(PerfilUsuario.OPERADOR, criado.perfil());
    }

    @ParameterizedTest
    @EnumSource(value = PerfilUsuario.class, names = {"ADMIN_GERAL", "ENTREGADOR"})
    @DisplayName("Deve recusar perfis que não pertencem a esta operação")
    void deveRecusarPerfil(PerfilUsuario perfil) {
        assertThrows(RegraDeNegocioException.class, () -> service.criar(estabelecimento.getId(),
                new CriarUsuarioRequest("X", "x@x.com", "senha-segura", perfil)));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Deve recusar e-mail já cadastrado")
    void deveRecusarEmailDuplicado() {
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        when(repository.existsByLoginEmail("ana@forno.com")).thenReturn(true);

        assertThrows(EmailJaCadastradoException.class, () -> service.criar(estabelecimento.getId(),
                new CriarUsuarioRequest("Ana", "ana@forno.com", "senha-segura", PerfilUsuario.OPERADOR)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve consultar, listar e desativar usuários da loja")
    void deveConsultarEAlterarAtivo() {
        Usuario operador = Fixtures.usuario(estabelecimento, PerfilUsuario.OPERADOR);
        when(buscador.usuario(estabelecimento.getId(), operador.getId())).thenReturn(operador);
        when(repository.findByEstabelecimentoId(estabelecimento.getId())).thenReturn(List.of(operador));
        when(repository.findByEstabelecimentoIdAndPerfil(estabelecimento.getId(), PerfilUsuario.OPERADOR))
                .thenReturn(List.of(operador));
        when(repository.save(operador)).thenReturn(operador);

        assertEquals(operador.getId(), service.buscarPorId(estabelecimento.getId(), operador.getId()).id());
        assertEquals(1, service.listarPorEstabelecimento(estabelecimento.getId()).size());
        assertEquals(1, service.listarPorEstabelecimentoEPerfil(estabelecimento.getId(), PerfilUsuario.OPERADOR).size());
        assertFalse(service.alterarAtivo(estabelecimento.getId(), operador.getId(), false).ativo());
    }
}
