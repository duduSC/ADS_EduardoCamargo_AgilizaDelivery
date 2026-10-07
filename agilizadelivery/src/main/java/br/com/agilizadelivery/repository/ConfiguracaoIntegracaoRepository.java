package br.com.agilizadelivery.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.enums.Provedor;

/**
 * Vinculo de cada loja com a sua conta no provedor de pedidos.
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface ConfiguracaoIntegracaoRepository extends JpaRepository<ConfiguracaoIntegracao, UUID> {

    /** Configuracao de uma loja em um provedor. */
    Optional<ConfiguracaoIntegracao> findByEstabelecimentoIdAndProvedor(UUID estabelecimentoId, Provedor provedor);

    /** Alimenta o ciclo de polling do worker, a cada 30 segundos (DVP 1.5.6). */
    List<ConfiguracaoIntegracao> findByAtivoTrue();

    /** Identifica a loja a partir do pedido recebido do provedor. */
    Optional<ConfiguracaoIntegracao> findByProvedorAndMerchantId(Provedor provedor, String merchantId);
}
