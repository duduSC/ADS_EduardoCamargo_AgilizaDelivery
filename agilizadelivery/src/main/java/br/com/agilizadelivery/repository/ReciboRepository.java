package br.com.agilizadelivery.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;

/**
 * Comprovantes do acerto financeiro (HU07).
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface ReciboRepository extends JpaRepository<Recibo, UUID> {

    /** Recibos de um periodo de operacao, para consolidar o fechamento. */
    List<Recibo> findByCaixaId(UUID caixaId);

    /** Recibos de um entregador por situacao. */
    List<Recibo> findByMotoboyIdAndStatus(UUID motoboyId, StatusRecibo status);

    /** Recibos de uma loja em um dia. */
    List<Recibo> findByEstabelecimentoIdAndDataReferencia(UUID estabelecimentoId, LocalDate dataReferencia);
}
