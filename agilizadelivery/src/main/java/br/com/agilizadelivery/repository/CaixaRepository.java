package br.com.agilizadelivery.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;

/**
 * Periodos de operacao. Ha no maximo um caixa por loja em cada dia.
 *
 * O CRUD basico vem do JpaRepository: save, findById, findAll,
 * existsById, count, deleteById e variantes.
 */
@Repository
public interface CaixaRepository extends JpaRepository<Caixa, UUID> {

    /** Caixa de um dia especifico. Corresponde ao unique (estabelecimento, data). */
    Optional<Caixa> findByEstabelecimentoIdAndDataReferencia(UUID estabelecimentoId, LocalDate dataReferencia);

    /** Caixas de uma loja em determinada situacao. */
    List<Caixa> findByEstabelecimentoIdAndStatus(UUID estabelecimentoId, StatusCaixa status);

    /** Indica se o dia ja foi aberto, antes de criar um novo caixa. */
    boolean existsByEstabelecimentoIdAndDataReferencia(UUID estabelecimentoId, LocalDate dataReferencia);
}
