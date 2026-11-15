package br.com.agilizadelivery.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;

/**
 * Vinculo de cada loja com a sua conta no provedor de pedidos.
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface ConfiguracaoIntegracaoRepository extends JpaRepository<ConfiguracaoIntegracao, UUID> {

    /** Configuracao de uma loja em um provedor. */
    Optional<ConfiguracaoIntegracao> findByEstabelecimentoIdAndProvedor(UUID estabelecimentoId, String provedor);

    List<ConfiguracaoIntegracao> findByEstabelecimentoId(UUID estabelecimentoId);

    Optional<ConfiguracaoIntegracao> findByIdAndEstabelecimentoId(UUID id, UUID estabelecimentoId);

    /** Alimenta o ciclo de polling do worker, a cada 30 segundos (DVP 1.5.6). */
    List<ConfiguracaoIntegracao> findByAtivoTrue();

    /**
     * Configuracoes ativas com a loja ja carregada, para o worker ler a
     * loja fora de transacao (T011).
     */
    @Query("select c from ConfiguracaoIntegracao c join fetch c.estabelecimento where c.ativo = true")
    List<ConfiguracaoIntegracao> findAtivasComEstabelecimento();

    /** Momento da ultima consulta bem-sucedida ao provedor (DVP 1.5.4). */
    @Transactional
    @Modifying
    @Query("update ConfiguracaoIntegracao c set c.ultimoPollingEm = :momento where c.id = :id")
    void registrarPolling(@Param("id") UUID id, @Param("momento") LocalDateTime momento);

    /** Identifica a loja a partir do pedido recebido do provedor. */
    Optional<ConfiguracaoIntegracao> findByProvedorAndMerchantId(String provedor, String merchantId);
}
