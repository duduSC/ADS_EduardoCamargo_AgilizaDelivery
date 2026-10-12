package br.com.agilizadelivery.repository;

import java.util.List;

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

    /** Contador para o indicador no painel, sem carregar os registros. */
    long countByStatus(StatusFalhaIntegracao status);
}
