package br.com.agilizadelivery.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.exception.EmailJaCadastradoException;
import br.com.agilizadelivery.repository.UsuarioRepository;
import br.com.agilizadelivery.service.Fixtures;

@ExtendWith(MockitoExtension.class)
class CadastroUsuarioTest {

    @Mock
    private UsuarioRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private CadastroUsuario cadastro;
    private Estabelecimento estabelecimento;

    @BeforeEach
    void setUp() {
        cadastro = new CadastroUsuario(repository, passwordEncoder);
        estabelecimento = Fixtures.estabelecimento();
    }

    @Test
    @DisplayName("Deve criar a credencial ativa, com e-mail normalizado e senha em hash BCrypt (RNF02)")
    void deveCriar() {
        when(repository.existsByLoginEmail("ana@forno.com")).thenReturn(false);
        when(passwordEncoder.encode("senha-segura")).thenReturn("$2a$10$hash");
        when(repository.save(any(Usuario.class))).then(returnsFirstArg());

        Usuario usuario = cadastro.criar(
                estabelecimento, " Ana ", " Ana@Forno.COM ", "senha-segura", PerfilUsuario.ADMIN_RESTAURANTE);

        assertEquals("ana@forno.com", usuario.getLoginEmail());
        assertEquals("Ana", usuario.getNome());
        assertEquals("$2a$10$hash", usuario.getSenhaHash());
        assertEquals(PerfilUsuario.ADMIN_RESTAURANTE, usuario.getPerfil());
        assertSame(estabelecimento, usuario.getEstabelecimento());
        assertTrue(usuario.getAtivo());
    }

    @Test
    @DisplayName("Deve recusar e-mail já cadastrado")
    void deveRecusarEmailDuplicado() {
        when(repository.existsByLoginEmail("ana@forno.com")).thenReturn(true);

        assertThrows(EmailJaCadastradoException.class, () ->
                cadastro.criar(estabelecimento, "Ana", "ana@forno.com", "senha-segura", PerfilUsuario.OPERADOR));
        verify(repository, never()).save(any());
    }
}
