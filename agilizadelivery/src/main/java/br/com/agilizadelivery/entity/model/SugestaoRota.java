package br.com.agilizadelivery.entity.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ordem de paradas sugerida pelo entregador para um lote.
 *
 * A rota principal e definida pelo operador no despacho e o entregador
 * deve segui-la. A sugestao e apenas registrada para que o operador a
 * avalie: ela nunca altera a rota oficial nem o repasse, que segue
 * apurado pela distancia loja -> cliente (DVP 1.5.5).
 *
 * Tabela append-only com PK bigint: nao herda EntidadeBase.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sugestao_rota")
public class SugestaoRota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private LoteEntrega lote;

    /** Autor da sugestao: o entregador do lote. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "motoboy_id", nullable = false)
    private Motoboy motoboy;

    /** Entregas na ordem proposta (posicao 0 = primeira parada). */
    @NotEmpty
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "sugestao_rota_parada", joinColumns = @JoinColumn(name = "sugestao_id"))
    @OrderColumn(name = "posicao")
    @Column(name = "entrega_id", nullable = false)
    private List<UUID> ordemEntregas = new ArrayList<>();

    /** Distancia viaria da rota sugerida, para comparacao pelo operador. */
    @PositiveOrZero
    @Column(name = "distancia_sugerida_km", precision = 6, scale = 2)
    private BigDecimal distanciaSugeridaKm;

    @Size(max = 255)
    @Column(name = "observacao", length = 255)
    private String observacao;

    @Column(name = "sugerida_em", nullable = false, updatable = false)
    private LocalDateTime sugeridaEm;

    @PrePersist
    protected void aoCriar() {
        if (this.sugeridaEm == null) {
            this.sugeridaEm = LocalDateTime.now();
        }
    }
}
