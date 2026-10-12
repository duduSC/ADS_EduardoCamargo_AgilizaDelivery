package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.EntregaStatusHistorico;

/**
 * Trilha de auditoria das transicoes de status (append-only).
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface EntregaStatusHistoricoRepository extends JpaRepository<EntregaStatusHistorico, Long> {

    /** Historico de uma entrega, do evento mais antigo ao mais recente. */
    List<EntregaStatusHistorico> findByEntregaIdOrderByRegistradoEmAsc(UUID entregaId);
}
