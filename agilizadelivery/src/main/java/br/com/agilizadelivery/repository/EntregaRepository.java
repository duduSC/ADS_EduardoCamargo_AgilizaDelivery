package br.com.agilizadelivery.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

/**
 * EntregaRepository
 */
public interface EntregaRepository extends JpaRepository<Entrega, UUID> {

    Optional<Entrega> findByIdAndEstabelecimentoId(UUID id, UUID estabelecimentoId);

    List<Entrega> findByEstabelecimentoIdAndStatusOrderByCriadoEmAsc(UUID estabelecimentoId, StatusEntrega status);

    /** Pendentes aptas ao mapa de despacho: sem revisao em aberto (HU01). */
    List<Entrega> findByEstabelecimentoIdAndStatusAndPendenteRevisaoFalseOrderByCriadoEmAsc(
            UUID estabelecimentoId,
            StatusEntrega status);

    /** Fila de revisao do estabelecimento, ordenada por criacao (HU01/HU03). */
    List<Entrega> findByEstabelecimentoIdAndPendenteRevisaoTrueOrderByCriadoEmAsc(UUID estabelecimentoId);

    boolean existsByEstabelecimentoId(UUID estabelecimentoId);

    /** Limite de seguranca do simulador de pedidos (T011). */
    long countByEstabelecimentoIdAndStatus(UUID estabelecimentoId, StatusEntrega status);

    List<Entrega> findByEstabelecimentoIdAndIdIn(UUID estabelecimentoId, Collection<UUID> ids);

    /** Entregas de um lote na ordem da rota definida pelo operador. */
    List<Entrega> findByLoteIdOrderByOrdemRotaAsc(UUID loteId);

    /** Lote como o aplicativo o le, com os itens da sacola numa consulta so (UC02). */
    @Query("""
            select e from Entrega e
            left join fetch e.pedidoExterno p
            left join fetch p.itens i
            where e.lote.id = :loteId
            order by e.ordemRota, e.id, i.id
            """)
    List<Entrega> findDoLoteComItens(@Param("loteId") UUID loteId);

    List<Entrega> findByReciboId(UUID reciboId);

    /** Entregas de um recibo da loja, na ordem em que foram finalizadas (D013). */
    List<Entrega> findByEstabelecimentoIdAndReciboIdOrderByFinalizadaEmAscIdAsc(
            UUID estabelecimentoId, UUID reciboId);

    /**
     * Entregas da loja finalizadas em [inicio, fim), com o entregador do
     * lote e o recibo numa consulta so, para o relatorio do dia (D013). A
     * cancelada antes do despacho vem sem lote.
     */
    @Query("""
            select e from Entrega e
            left join fetch e.lote l
            left join fetch l.motoboy m
            left join fetch m.usuario
            left join fetch e.recibo
            where e.estabelecimento.id = :estabelecimentoId
              and e.finalizadaEm >= :inicio
              and e.finalizadaEm < :fim
            """)
    List<Entrega> findFinalizadasNoPeriodoComEntregador(
            @Param("estabelecimentoId") UUID estabelecimentoId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim);

    /** Entregas finalizadas por um entregador no periodo (UC03 e UC08). */
    @EntityGraph(attributePaths = "faixaPreco")
    List<Entrega> findByEstabelecimentoIdAndLoteMotoboyIdAndFinalizadaEmBetweenOrderByFinalizadaEmAsc(
            UUID estabelecimentoId,
            UUID motoboyId,
            LocalDateTime inicio,
            LocalDateTime fim);
}
