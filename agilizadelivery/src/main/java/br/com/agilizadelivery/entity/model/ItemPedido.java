package br.com.agilizadelivery.entity.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Item do pedido importado, para conferencia da sacola (UC02).
 *
 * Tabela com PK bigint: nao herda EntidadeBase. O ciclo de vida acompanha
 * o PedidoExterno, que o gerencia por cascade.
 *
 * Entregas avulsas nao possuem itens; nelas o conteudo fica em
 * Entrega.observacoes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_pedido")
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_externo_id", nullable = false)
    private PedidoExterno pedidoExterno;

    /** Nome do item no provedor. */
    @NotBlank
    @Size(max = 200)
    @Column(name = "descricao", nullable = false, length = 200)
    private String descricao;

    @NotNull
    @Positive
    @Column(name = "quantidade", nullable = false)
    private Short quantidade;

    @PositiveOrZero
    @Column(name = "valor_unitario", precision = 10, scale = 2)
    private BigDecimal valorUnitario;

    /** Observacao do cliente sobre o item. */
    @Size(max = 255)
    @Column(name = "observacao", length = 255)
    private String observacao;
}
