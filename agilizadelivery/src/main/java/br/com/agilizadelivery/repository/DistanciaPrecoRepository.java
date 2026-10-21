package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.DistanciaPreco;

/**
 * Tabela de precos e faixas de distancia dos estabelecimentos.
 *
 * Utilizado pelo calculo de repasse de entregas e manutencao da tabela de precos (HU10).
 */
@Repository
public interface DistanciaPrecoRepository extends JpaRepository<DistanciaPreco, UUID> {

    /**
     * Retorna todas as faixas ativas de uma loja ordenadas pelo km inicial.
     * Necessario para encontrar a faixa correspondente ou validar integridade (sem lacunas).
     */
    List<DistanciaPreco> findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(UUID estabelecimentoId);

    /**
     * Retorna o historico completo de faixas de uma loja, ativas e inativas.
     */
    List<DistanciaPreco> findByEstabelecimentoIdOrderByVigenteDesdeDescDeKmAsc(UUID estabelecimentoId);
}
