package br.com.agilizadelivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.agilizadelivery.dto.request.LoginRequest;
import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.exception.AcessoNegadoException;
import br.com.agilizadelivery.exception.CredenciaisInvalidasException;
import br.com.agilizadelivery.exception.EstabelecimentoInativoException;
import br.com.agilizadelivery.exception.UsuarioBloqueadoException;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.repository.UsuarioRepository;
import br.com.agilizadelivery.service.impl.AutenticacaoServiceImpl;

@ExtendWith(MockitoExtension.class)
class AutenticacaoServiceTest {

    private static final String SENHA = "senha-segura";
    private static final String HASH_FICTICIO = "$2a$10$ficticio";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private MotoboyRepository motoboyRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AutenticacaoService service;
    private Estabelecimento estabelecimento;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(HASH_FICTICIO);
        service = new AutenticacaoServiceImpl(usuarioRepository, motoboyRepository, passwordEncoder);
        estabelecimento = Fixtures.estabelecimento();
    }

    private Usuario cadastrado(PerfilUsuario perfil) {
        Usuario usuario = Fixtures.usuario(perfil == PerfilUsuario.ADMIN_GERAL ? null : estabelecimento, perfil);
        when(usuarioRepository.findByLoginEmail(usuario.getLoginEmail())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(SENHA, usuario.getSenhaHash())).thenReturn(true);
        return usuario;
    }

    private LoginRequest login(Usuario usuario) {
        return new LoginRequest(" " + usuario.getLoginEmail().toUpperCase() + " ", SENHA);
    }

    @Test
    @DisplayName("Deve autenticar o operador no backoffice e registrar o último acesso")
    void deveAutenticarOperador() {
        Usuario operador = cadastrado(PerfilUsuario.OPERADOR);

        UsuarioAutenticadoResponse autenticado = service.autenticar(login(operador), CanalAcesso.BACKOFFICE_WEB);

        assertEquals(operador.getId(), autenticado.usuarioId());
        assertEquals(estabelecimento.getId(), autenticado.estabelecimentoId());
        assertEquals(PerfilUsuario.OPERADOR, autenticado.perfil());
        assertNull(autenticado.motoboyId());
        assertNotNull(operador.getUltimoAcessoEm());
        verify(usuarioRepository).save(operador);
    }

    @Test
    @DisplayName("Deve autenticar o administrador geral, que não pertence a nenhuma loja")
    void deveAutenticarAdminGeral() {
        Usuario admin = cadastrado(PerfilUsuario.ADMIN_GERAL);

        UsuarioAutenticadoResponse autenticado = service.autenticar(login(admin), CanalAcesso.BACKOFFICE_WEB);

        assertNull(autenticado.estabelecimentoId());
    }

    @Test
    @DisplayName("Deve autenticar o entregador no aplicativo, devolvendo o seu cadastro")
    void deveAutenticarEntregador() {
        Motoboy motoboy = Fixtures.motoboy(estabelecimento);
        Usuario entregador = motoboy.getUsuario();
        when(usuarioRepository.findByLoginEmail(entregador.getLoginEmail())).thenReturn(Optional.of(entregador));
        when(passwordEncoder.matches(SENHA, entregador.getSenhaHash())).thenReturn(true);
        when(motoboyRepository.findByUsuarioId(entregador.getId())).thenReturn(Optional.of(motoboy));

        UsuarioAutenticadoResponse autenticado = service.autenticar(login(entregador), CanalAcesso.APLICATIVO);

        assertEquals(motoboy.getId(), autenticado.motoboyId());
    }

    @Test
    @DisplayName("Deve recusar e-mail inexistente com mensagem genérica, comparando o hash fictício (UC07 E1)")
    void deveRecusarEmailInexistente() {
        when(usuarioRepository.findByLoginEmail("ninguem@forno.com")).thenReturn(Optional.empty());

        CredenciaisInvalidasException erro = assertThrows(CredenciaisInvalidasException.class, () ->
                service.autenticar(new LoginRequest("ninguem@forno.com", SENHA), CanalAcesso.BACKOFFICE_WEB));

        assertEquals("E-mail ou senha inválidos.", erro.getMessage());
        verify(passwordEncoder).matches(SENHA, HASH_FICTICIO);
    }

    @Test
    @DisplayName("Deve recusar senha incorreta com a mesma mensagem genérica (UC07 E1)")
    void deveRecusarSenhaIncorreta() {
        Usuario operador = Fixtures.usuario(estabelecimento, PerfilUsuario.OPERADOR);
        when(usuarioRepository.findByLoginEmail(operador.getLoginEmail())).thenReturn(Optional.of(operador));
        when(passwordEncoder.matches("errada", operador.getSenhaHash())).thenReturn(false);

        CredenciaisInvalidasException erro = assertThrows(CredenciaisInvalidasException.class, () ->
                service.autenticar(new LoginRequest(operador.getLoginEmail(), "errada"), CanalAcesso.BACKOFFICE_WEB));

        assertEquals("E-mail ou senha inválidos.", erro.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve recusar usuário inativo (UC07 E3)")
    void deveRecusarUsuarioInativo() {
        Usuario operador = cadastrado(PerfilUsuario.OPERADOR);
        operador.setAtivo(false);

        assertThrows(UsuarioBloqueadoException.class, () ->
                service.autenticar(login(operador), CanalAcesso.BACKOFFICE_WEB));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve recusar perfil incompatível com o cliente (UC07 E4)")
    void deveRecusarCanalIncompativel() {
        Usuario operador = cadastrado(PerfilUsuario.OPERADOR);
        assertThrows(AcessoNegadoException.class, () ->
                service.autenticar(login(operador), CanalAcesso.APLICATIVO));

        Usuario entregador = cadastrado(PerfilUsuario.ENTREGADOR);
        assertThrows(AcessoNegadoException.class, () ->
                service.autenticar(login(entregador), CanalAcesso.BACKOFFICE_WEB));
    }

    @Test
    @DisplayName("Deve recusar usuário de estabelecimento inativo (UC07 E2, HU12)")
    void deveRecusarLojaInativa() {
        Usuario operador = cadastrado(PerfilUsuario.OPERADOR);
        estabelecimento.setStatus(StatusEstabelecimento.INATIVO);

        assertThrows(EstabelecimentoInativoException.class, () ->
                service.autenticar(login(operador), CanalAcesso.BACKOFFICE_WEB));
    }

    @Test
    @DisplayName("Deve recusar entregador com cadastro BLOQUEADO (UC07 E3)")
    void deveRecusarEntregadorBloqueado() {
        Motoboy motoboy = Fixtures.motoboy(estabelecimento);
        motoboy.setStatusCadastro(StatusCadastroMotoboy.BLOQUEADO);
        Usuario entregador = motoboy.getUsuario();
        when(usuarioRepository.findByLoginEmail(entregador.getLoginEmail())).thenReturn(Optional.of(entregador));
        when(passwordEncoder.matches(SENHA, entregador.getSenhaHash())).thenReturn(true);
        when(motoboyRepository.findByUsuarioId(entregador.getId())).thenReturn(Optional.of(motoboy));

        assertThrows(UsuarioBloqueadoException.class, () ->
                service.autenticar(login(entregador), CanalAcesso.APLICATIVO));
        verify(usuarioRepository, never()).save(any());
    }
}
