package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;

/**
 * Lotes despachados aos entregadores.
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface LoteEntregaRepository extends JpaRepository<LoteEntrega, UUID> {

    /** Lotes de um entregador em determinada situacao; em aberto, no aplicativo. */
    List<LoteEntrega> findByMotoboyIdAndStatus(UUID motoboyId, StatusLote status);

    /** Lotes de uma loja por situacao, para o painel de despacho. */
    List<LoteEntrega> findByEstabelecimentoIdAndStatus(UUID estabelecimentoId, StatusLote status);
}
