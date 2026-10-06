package br.com.agilizadelivery.entity.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trilha de auditoria dos ajustes manuais de repasse (UC03 A1).
 *
 * "Congelado" significa imune a mudancas na tabela de precos, nao imune a
 * correcoes: enquanto a entrega nao estiver vinculada a um recibo, o
 * operador pode ajustar o valor, sempre com motivo obrigatorio e com o
 * valor anterior preservado aqui.
 *
 * Tabela append-only com PK bigint: nao herda EntidadeBase.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "entrega_ajuste_valor")
public class EntregaAjusteValor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /** Deve estar com recibo nulo no momento do ajuste. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entrega_id", nullable = false)
    private Entrega entrega;

    /** Autor do ajuste. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @PositiveOrZero
    @Column(name = "valor_anterior", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorAnterior;

    @NotNull
    @PositiveOrZero
    @Column(name = "valor_novo", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorNovo;

    /** Justificativa obrigatoria do ajuste. */
    @NotBlank
    @Size(max = 255)
    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    @Column(name = "ajustado_em", nullable = false, updatable = false)
    private LocalDateTime ajustadoEm;

    @PrePersist
    protected void aoCriar() {
        if (this.ajustadoEm == null) {
            this.ajustadoEm = LocalDateTime.now();
        }
    }
}
