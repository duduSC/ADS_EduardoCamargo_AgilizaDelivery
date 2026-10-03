package br.com.agilizadelivery.entity.model;

import java.time.LocalDateTime;

import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trilha de auditoria de cada transicao de status da entrega (DVP 1.5.4).
 *
 * Como o sistema guarda apenas a ultima posicao do entregador (RNF07),
 * a coordenada registrada aqui, no momento de cada mudanca de status, e
 * a unica evidencia historica de onde ele estava.
 *
 * O horario e o do evento na origem: eventos enfileirados offline sao
 * sincronizados depois preservando o momento original (RNF09).
 *
 * Tabela append-only com PK bigint: nao herda EntidadeBase.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "entrega_status_historico")
public class EntregaStatusHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entrega_id", nullable = false)
    private Entrega entrega;

    /** Nulo na primeira transicao, quando a entrega e criada. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", length = 25)
    private StatusEntrega statusAnterior;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, length = 25)
    private StatusEntrega statusNovo;

    /** Autor da transicao. Nulo quando a mudanca parte do provedor. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_usuario_id")
    private Usuario registradoPorUsuario;

    /** Onde o entregador estava ao registrar o evento. */
    @Valid
    @Embedded
    private Coordenada coordenada;

    /** Horario do evento na origem, nao o da sincronizacao (RNF09). */
    @Column(name = "registrado_em", nullable = false, updatable = false)
    private LocalDateTime registradoEm;

    @PrePersist
    protected void aoCriar() {
        if (this.registradoEm == null) {
            this.registradoEm = LocalDateTime.now();
        }
    }
}
