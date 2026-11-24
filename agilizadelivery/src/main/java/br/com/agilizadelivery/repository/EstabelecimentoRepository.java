package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import jakarta.persistence.LockModeType;

/**
 * Restaurantes parceiros. Raiz da multilocacao.
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, UUID> {

    /** Busca pelo CNPJ, unico no sistema (HU11). */
    Optional<Estabelecimento> findByCnpj(String cnpj);

    /** Verifica duplicidade antes do cadastro (UC06 E1). */
    boolean existsByCnpj(String cnpj);

    /** SELECT ... FOR UPDATE na linha da loja, a trava de BuscadorTenant.travarLoja. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estabelecimento e where e.id = :id")
    Optional<Estabelecimento> bloquearPorId(@Param("id") UUID id);

    /** Lista as lojas por situacao cadastral; ATIVO para as que operam (HU12). */
    List<Estabelecimento> findByStatus(StatusEstabelecimento status);
}
