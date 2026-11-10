package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.SugestaoRota;

/**
 * Sugestoes de rota feitas pelos entregadores (append-only).
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface SugestaoRotaRepository extends JpaRepository<SugestaoRota, Long> {

    /** Sugestoes de um lote, da mais antiga para a mais recente. */
    List<SugestaoRota> findByLoteEstabelecimentoIdAndLoteIdOrderBySugeridaEmAsc(UUID estabelecimentoId, UUID loteId);
}
