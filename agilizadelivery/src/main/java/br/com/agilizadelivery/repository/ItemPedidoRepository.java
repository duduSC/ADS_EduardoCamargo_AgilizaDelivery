package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.ItemPedido;

/**
 * Itens dos pedidos importados.
 *
 * O ciclo de vida e gerenciado por PedidoExterno, que os salva e remove
 * em cascata. Este repositorio serve apenas para consulta direta.
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {

    /** Itens de um pedido, para conferencia da sacola (UC02). */
    List<ItemPedido> findByPedidoExternoId(UUID pedidoExternoId);
}
