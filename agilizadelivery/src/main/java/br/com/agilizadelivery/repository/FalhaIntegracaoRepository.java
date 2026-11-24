package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.FalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.StatusFalhaIntegracao;

/**
 * Fila de erro da integracao com o provedor (RNF04).
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface FalhaIntegracaoRepository extends JpaRepository<FalhaIntegracao, Long> {

    List<FalhaIntegracao> findByConfiguracaoIntegracaoEstabelecimentoIdAndStatus(
            UUID estabelecimentoId,
            StatusFalhaIntegracao status);

    Optional<FalhaIntegracao> findByIdAndConfiguracaoIntegracaoEstabelecimentoId(
            Long id,
            UUID estabelecimentoId);

    long countByConfiguracaoIntegracaoEstabelecimentoIdAndStatus(
            UUID estabelecimentoId,
            StatusFalhaIntegracao status);
}
