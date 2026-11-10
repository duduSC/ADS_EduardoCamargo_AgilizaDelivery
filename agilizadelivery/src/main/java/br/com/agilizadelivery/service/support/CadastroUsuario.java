package br.com.agilizadelivery.service.support;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.exception.EmailJaCadastradoException;
import br.com.agilizadelivery.repository.UsuarioRepository;

/**
 * Criacao de credenciais de acesso, comum ao cadastro de operadores,
 * entregadores (HU09) e do administrador inicial da loja (UC06).
 *
 * O e-mail de login e unico e normalizado; a senha e gravada apenas como
 * hash BCrypt (RNF02).
 */
@Component
public class CadastroUsuario {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public CadastroUsuario(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario criar(
            Estabelecimento estabelecimento,
            String nome,
            String loginEmail,
            String senha,
            PerfilUsuario perfil) {
        String email = normalizarEmail(loginEmail);
        if (repository.existsByLoginEmail(email)) {
            throw new EmailJaCadastradoException(email);
        }

        Usuario usuario = new Usuario();
        usuario.setEstabelecimento(estabelecimento);
        usuario.setNome(nome.trim());
        usuario.setLoginEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        return repository.save(usuario);
    }

    public static String normalizarEmail(String loginEmail) {
        return loginEmail == null ? null : loginEmail.trim().toLowerCase(Locale.ROOT);
    }
}
