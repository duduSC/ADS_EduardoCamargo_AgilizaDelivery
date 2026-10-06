package br.com.agilizadelivery.entity.model.base;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

/**
 * Classe-mae das entidades de negocio com chave primaria UUID.
 *
 * Nao e uma entidade: @MappedSuperclass nao gera tabela propria. Os campos
 * declarados aqui sao herdados pelas subclasses como se tivessem sido
 * escritos nelas, virando colunas na tabela de cada uma.
 *
 * Concentra o que se repete em toda entidade: o identificador e as datas
 * de controle (criado_em e atualizado_em), preenchidas automaticamente
 * pelos callbacks do JPA.
 *
 * As tabelas de log com PK bigint (ItemPedido, EntregaStatusHistorico,
 * EntregaAjusteValor e FalhaIntegracao) nao herdam desta classe: sao
 * append-only e declaram o proprio identificador.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void aoCriar() {
        LocalDateTime agora = LocalDateTime.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    @PreUpdate
    protected void aoAtualizar() {
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Igualdade pelo identificador.
     *
     * Usa getClass() em vez de instanceof por causa dos proxies do Hibernate:
     * uma entidade carregada em modo lazy e, na verdade, uma subclasse gerada
     * em tempo de execucao. Por isso a comparacao e feita sobre o id, e o
     * hashCode e constante por classe, para nao quebrar quando a entidade
     * entra em um HashSet antes de ser persistida (id ainda nulo).
     */
    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        if (objeto == null || !(objeto instanceof EntidadeBase outra)) {
            return false;
        }
        return this.id != null && this.id.equals(outra.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
