package br.com.agilizadelivery.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import br.com.agilizadelivery.service.AutenticacaoService;
import br.com.agilizadelivery.service.CanalAcesso;
import br.com.agilizadelivery.service.support.CadastroUsuario;

/**
 * Autenticacao por e-mail e senha (UC07).
 *
 * Ordem das verificacoes: credenciais (E1, mensagem generica), usuario
 * ativo (E3), perfil compativel com o cliente (E4), loja ATIVA exceto para
 * ADMIN_GERAL (E2) e, para o entregador, cadastro ATIVO (E3).
 */
@Service
@Transactional(readOnly = true)
public class AutenticacaoServiceImpl implements AutenticacaoService {

    private final UsuarioRepository usuarioRepository;
    private final MotoboyRepository motoboyRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Comparado quando o e-mail nao existe, para que a resposta leve o
     * mesmo tempo e nao revele quais e-mails estao cadastrados.
     */
    private final String hashFicticio;

    public AutenticacaoServiceImpl(
            UsuarioRepository usuarioRepository,
            MotoboyRepository motoboyRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.motoboyRepository = motoboyRepository;
        this.passwordEncoder = passwordEncoder;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public UsuarioAutenticadoResponse autenticar(LoginRequest request, CanalAcesso canal) {
        return liberarAcesso(validarCredenciais(request), canal);
    }

    @Override
    @Transactional
    public UsuarioAutenticadoResponse reautenticar(UUID usuarioId, CanalAcesso canal) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow(CredenciaisInvalidasException::new);
        return liberarAcesso(usuario, canal);
    }

    private UsuarioAutenticadoResponse liberarAcesso(Usuario usuario, CanalAcesso canal) {
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new UsuarioBloqueadoException(usuario.getLoginEmail(), "o usuário está inativo");
        }
        exigirCanalDoPerfil(usuario.getPerfil(), canal);
        if (!usuario.isAdminGeral()) {
            exigirEstabelecimentoAtivo(usuario.getEstabelecimento());
        }
        UUID motoboyId = usuario.getPerfil() == PerfilUsuario.ENTREGADOR ? motoboyAtivo(usuario).getId() : null;

        usuario.setUltimoAcessoEm(LocalDateTime.now());
        usuarioRepository.save(usuario);
        return new UsuarioAutenticadoResponse(
                usuario.getId(),
                usuario.getEstabelecimento() == null ? null : usuario.getEstabelecimento().getId(),
                motoboyId,
                usuario.getNome(),
                usuario.getPerfil());
    }

    private Usuario validarCredenciais(LoginRequest request) {
        Optional<Usuario> encontrado = usuarioRepository
                .findByLoginEmail(CadastroUsuario.normalizarEmail(request.loginEmail()));
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio);
        boolean senhaConfere = passwordEncoder.matches(request.senha(), hash);
        if (encontrado.isEmpty() || !senhaConfere) {
            throw new CredenciaisInvalidasException();
        }
        return encontrado.get();
    }

    private static void exigirCanalDoPerfil(PerfilUsuario perfil, CanalAcesso canal) {
        CanalAcesso esperado = perfil == PerfilUsuario.ENTREGADOR ? CanalAcesso.APP : CanalAcesso.BACKOFFICE;
        if (canal != esperado) {
            throw new AcessoNegadoException(perfil == PerfilUsuario.ENTREGADOR
                    ? "Entregadores acessam o sistema apenas pelo aplicativo."
                    : "Este perfil acessa o sistema apenas pelo backoffice Web.");
        }
    }

    private static void exigirEstabelecimentoAtivo(Estabelecimento estabelecimento) {
        if (estabelecimento == null || estabelecimento.getStatus() != StatusEstabelecimento.ATIVO) {
            throw new EstabelecimentoInativoException(
                    estabelecimento == null ? "não informado" : estabelecimento.getNomeFantasia());
        }
    }

    private Motoboy motoboyAtivo(Usuario usuario) {
        Motoboy motoboy = motoboyRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new UsuarioBloqueadoException(
                        usuario.getLoginEmail(), "não há cadastro de entregador para este usuário"));
        if (motoboy.getStatusCadastro() != StatusCadastroMotoboy.ATIVO) {
            throw new UsuarioBloqueadoException(usuario.getLoginEmail(), "o cadastro do entregador está bloqueado");
        }
        return motoboy;
    }
}
