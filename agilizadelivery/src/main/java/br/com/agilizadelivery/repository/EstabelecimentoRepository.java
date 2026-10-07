package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;

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

    /** Lista as lojas por situacao cadastral; ATIVO para as que operam (HU12). */
    List<Estabelecimento> findByStatus(StatusEstabelecimento status);
}
