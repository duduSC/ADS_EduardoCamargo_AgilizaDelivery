package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.entity.model.enums.Provedor;

/**
 * Copia fiel dos pedidos recebidos do provedor (RNF13).
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface PedidoExternoRepository extends JpaRepository<PedidoExterno, UUID> {

    /** Chave de idempotencia: impede que o polling importe o mesmo pedido duas vezes (HU03). */
    Optional<PedidoExterno> findByProvedorAndIdExterno(Provedor provedor, String idExterno);

    /** Mesma verificacao, sem carregar o registro. Preferir no laco do worker. */
    boolean existsByProvedorAndIdExterno(Provedor provedor, String idExterno);

    /** Pedidos importados que ainda nao viraram entrega. */
    List<PedidoExterno> findByEstabelecimentoIdAndProcessadoEmIsNull(UUID estabelecimentoId);
}
