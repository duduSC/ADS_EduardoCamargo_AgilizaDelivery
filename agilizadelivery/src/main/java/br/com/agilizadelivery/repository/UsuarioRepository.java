package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;

/**
 * Credenciais de acesso de todos os perfis.
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /** Base da autenticacao (UC07). */
    Optional<Usuario> findByLoginEmail(String loginEmail);

    /** Verifica duplicidade de login antes do cadastro. */
    boolean existsByLoginEmail(String loginEmail);

    /** Usuarios de uma loja. ADMIN_GERAL nao aparece aqui: e global, com estabelecimento nulo. */
    List<Usuario> findByEstabelecimentoId(UUID estabelecimentoId);

    /** Usuarios de uma loja por perfil, por exemplo os operadores. */
    List<Usuario> findByEstabelecimentoIdAndPerfil(UUID estabelecimentoId, PerfilUsuario perfil);

    /** Usuarios globais, sem vinculo com loja (ADMIN_GERAL). */
    List<Usuario> findByEstabelecimentoIsNull();
}
