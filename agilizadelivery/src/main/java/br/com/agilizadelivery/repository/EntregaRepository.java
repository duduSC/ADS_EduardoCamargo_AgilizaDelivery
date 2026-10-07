package br.com.agilizadelivery.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import br.com.agilizadelivery.entity.model.Entrega;

/**
 * EntregaRepository
 */
public interface EntregaRepository extends JpaRepository<Entrega, UUID> {

    // @Modifying 
    // @Query ("SELECT e FROM Entrega e INNER JOIN LoteEntrega l on e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE e.lote.id = :loteId AND l.motoboy.id = :motoboyId AND e.dataEntrega BETWEEN :dataInicio AND :dataFim")
    // Entrega buscaPorLoteMotoboy(UUID loteId, UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);

    // @Modifying 
    // @Query ("SELECT e FROM Entrega e INNER JOIN Recibo r ON e.recibo.id = r.id INNER JOIN LoteEntrega l on e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE e.recibo.id = :reciboId AND l.motoboy.id = :motoboyId AND e.dataEntrega BETWEEN :dataInicio AND :dataFim")
    // Entrega buscaPorReciboMotoboy(UUID reciboId, UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);

    // @Modifying 
    // @Query ("SELECT COUNT(e) FROM Entrega e INNER JOIN LoteEntrega l on e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE e.lote.id = :loteId AND l.motoboy.id = :motoboyId AND e.dataEntrega BETWEEN :dataInicio AND :dataFim")
    // Number countEntregasPorLoteMotoboy(UUID loteId, UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);

    // @Modifying
    // @Query ("SELECT e FROM Entrega e INNER JOIN LoteEntrega l on e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE l.motoboy.id = :motoboyId AND e.pagoOnline = false AND e.dataEntrega BETWEEN :dataInicio AND :dataFim")
    // List<Entrega> countEntregasParaReceber(UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);

    // @Modifying
    // @Query ("SELECT SUM(e.distanciaKm) FROM Entrega e INNER JOIN LoteEntrega l ON e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE l.motoboy.id = :motoboyId AND e.dataEntrega BETWEEN :dataInicio AND :dataFim")
    // Number countTotalKmPercorridosPorMotoboy(UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);

    // @Modifying
    // @Query ("SELECT COUNT(e) FROM Entrega e INNER JOIN LoteEntrega l ON e.lote.id = l.id INNER JOIN Motoboy m ON l.motoboy.id = m.id WHERE l.motoboy.id = :motoboyId AND e.pendente_revisao = true AND e.dataEntrega BETWEEN :dataInicio AND :dataFim")
    // List<Entrega> countEntregasPendentesPorMotoboy(UUID motoboyId, LocalDateTime dataInicio, LocalDateTime dataFim);
}
