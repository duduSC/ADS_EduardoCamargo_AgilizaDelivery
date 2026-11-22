package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;

/**
 * MotoboyRepository
 */
@Repository
public interface MotoboyRepository extends JpaRepository<Motoboy, UUID> {

    
    Boolean existsByCpfHash(String cpfHash);

    /** Cadastro do entregador a partir da credencial, na autenticacao (UC07). */
    @EntityGraph(attributePaths = "usuario")
    Optional<Motoboy> findByUsuarioId(UUID usuarioId);

    /** O vinculo com a loja vem do usuario associado ao entregador. */
    Optional<Motoboy> findByIdAndUsuarioEstabelecimentoId(UUID id, UUID estabelecimentoId);

    @EntityGraph(attributePaths = "usuario")
    List<Motoboy> findByUsuarioEstabelecimentoId(UUID estabelecimentoId);

    @EntityGraph(attributePaths = "usuario")
    List<Motoboy> findByUsuarioEstabelecimentoIdAndStatusCadastro(
            UUID estabelecimentoId,
            StatusCadastroMotoboy statusCadastro);

    @EntityGraph(attributePaths = "usuario")
    List<Motoboy> findByUsuarioEstabelecimentoIdAndStatusDisponibilidade(
            UUID estabelecimentoId,
            StatusDisponibilidadeMotoboy statusDisponibilidade);
}
