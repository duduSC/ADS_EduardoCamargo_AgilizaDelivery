package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.EntregaAjusteValor;

/**
 * Trilha de auditoria dos ajustes manuais de repasse (append-only).
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface EntregaAjusteValorRepository extends JpaRepository<EntregaAjusteValor, Long> {

    List<EntregaAjusteValor> findByEntregaEstabelecimentoIdAndEntregaIdOrderByAjustadoEmAsc(
            UUID estabelecimentoId,
            UUID entregaId);
}
