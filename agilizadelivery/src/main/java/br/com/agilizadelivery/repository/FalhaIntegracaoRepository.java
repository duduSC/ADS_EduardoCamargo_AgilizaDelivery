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

    /** Falhas por situacao; PENDENTE alimenta o alerta de integracao degradada. */
    List<FalhaIntegracao> findByStatus(StatusFalhaIntegracao status);

    List<FalhaIntegracao> findByConfiguracaoIntegracaoEstabelecimentoIdAndStatus(
            UUID estabelecimentoId,
            StatusFalhaIntegracao status);

    Optional<FalhaIntegracao> findByIdAndConfiguracaoIntegracaoEstabelecimentoId(
            Long id,
            UUID estabelecimentoId);

    /** Contador para o indicador no painel, sem carregar os registros. */
    long countByStatus(StatusFalhaIntegracao status);

    long countByConfiguracaoIntegracaoEstabelecimentoIdAndStatus(
            UUID estabelecimentoId,
            StatusFalhaIntegracao status);
}
