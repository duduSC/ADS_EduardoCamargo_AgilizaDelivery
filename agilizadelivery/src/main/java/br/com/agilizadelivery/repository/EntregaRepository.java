package br.com.agilizadelivery.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

/**
 * EntregaRepository
 */
public interface EntregaRepository extends JpaRepository<Entrega, UUID> {
 
    @Query ("SELECT e FROM Entrega e INNER JOIN LoteEntrega l on e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE e.lote.id = :loteId AND l.motoboy.id = :motoboyId AND e.finalizadaEm BETWEEN :dataInicio AND :dataFim")
    Entrega buscaPorLoteMotoboy(UUID loteId, UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);
 
    @Query ("SELECT e FROM Entrega e INNER JOIN Recibo r ON e.recibo.id = r.id INNER JOIN LoteEntrega l on e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE e.recibo.id = :reciboId AND l.motoboy.id = :motoboyId AND e.finalizadaEm BETWEEN :dataInicio AND :dataFim")
    Entrega buscaPorReciboMotoboy(UUID reciboId, UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);
 
    @Query ("SELECT COUNT(e) FROM Entrega e INNER JOIN LoteEntrega l on e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE e.lote.id = :loteId AND l.motoboy.id = :motoboyId AND e.finalizadaEm BETWEEN :dataInicio AND :dataFim")
    Number countEntregasPorLoteMotoboy(UUID loteId, UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);

    @Query ("SELECT e FROM Entrega e INNER JOIN LoteEntrega l on e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE l.motoboy.id = :motoboyId AND e.pagoOnline = false AND e.finalizadaEm BETWEEN :dataInicio AND :dataFim")
    List<Entrega> countEntregasParaReceber(UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);

    @Query ("SELECT SUM(e.distanciaKm) FROM Entrega e INNER JOIN LoteEntrega l ON e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE l.motoboy.id = :motoboyId AND e.finalizadaEm BETWEEN :dataInicio AND :dataFim")
    Number countTotalKmPercorridosPorMotoboy(UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);

    @Query ("SELECT COUNT(e) FROM Entrega e INNER JOIN LoteEntrega l ON e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE l.motoboy.id = :motoboyId AND e.pendenteRevisao = true AND e.finalizadaEm BETWEEN :dataInicio AND :dataFim")
    List<Entrega> countEntregasPendentesPorMotoboy(UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);

    Optional<Entrega> findByIdAndEstabelecimentoId(UUID id, UUID estabelecimentoId);

    List<Entrega> findByEstabelecimentoIdAndStatusOrderByCriadoEmAsc(UUID estabelecimentoId, StatusEntrega status);

    /** Pendentes aptas ao mapa de despacho: sem revisao em aberto (HU01). */
    List<Entrega> findByEstabelecimentoIdAndStatusAndPendenteRevisaoFalseOrderByCriadoEmAsc(
            UUID estabelecimentoId,
            StatusEntrega status);

    /** Fila de revisao do estabelecimento, ordenada por criacao (HU01/HU03). */
    List<Entrega> findByEstabelecimentoIdAndPendenteRevisaoTrueOrderByCriadoEmAsc(UUID estabelecimentoId);

    List<Entrega> findByEstabelecimentoIdAndIdIn(UUID estabelecimentoId, Collection<UUID> ids);

    /** Entregas de um lote na ordem da rota definida pelo operador. */
    List<Entrega> findByLoteIdOrderByOrdemRotaAsc(UUID loteId);

    List<Entrega> findByReciboId(UUID reciboId);

    /** Entregas finalizadas por um entregador no periodo (UC03 e UC08). */
    List<Entrega> findByEstabelecimentoIdAndLoteMotoboyIdAndFinalizadaEmBetweenOrderByFinalizadaEmAsc(
            UUID estabelecimentoId,
            UUID motoboyId,
            LocalDateTime inicio,
            LocalDateTime fim);
}
