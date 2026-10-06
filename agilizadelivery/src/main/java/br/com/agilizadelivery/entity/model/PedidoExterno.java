package br.com.agilizadelivery.entity.model;

import br.com.agilizadelivery.entity.model.base.EntidadeDoEstabelecimento;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import br.com.agilizadelivery.entity.model.enums.FormaPagamento;
import br.com.agilizadelivery.entity.model.enums.Provedor;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Copia fiel do pedido recebido do provedor (RNF13).
 *
 * O par (provedor, id_externo) e unico: e a chave de idempotencia que
 * garante que o polling de 30 segundos nao duplique pedidos (HU03).
 *
 * O payload original e preservado em JSON para auditoria e reprocessamento.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "pedido_externo",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pedido_externo_provedor_id_externo",
                columnNames = {"provedor", "id_externo"}))
public class PedidoExterno extends EntidadeDoEstabelecimento {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "provedor", nullable = false, length = 20)
    private Provedor provedor;

    /** Identificador do pedido no provedor. */
    @NotBlank
    @Size(max = 100)
    @Column(name = "id_externo", nullable = false, length = 100)
    private String idExterno;

    /** Numero curto que o cliente e o restaurante enxergam. */
    @Size(max = 20)
    @Column(name = "numero_exibicao", length = 20)
    private String numeroExibicao;

    /** Status no provedor, para conciliacao e deteccao de cancelamentos. */
    @Size(max = 30)
    @Column(name = "status_externo", length = 30)
    private String statusExterno;

    @NotBlank
    @Size(max = 120)
    @Column(name = "nome_cliente", nullable = false, length = 120)
    private String nomeCliente;

    @Size(max = 20)
    @Column(name = "telefone_cliente", length = 20)
    private String telefoneCliente;

    @PositiveOrZero
    @Column(name = "valor_itens", precision = 10, scale = 2)
    private BigDecimal valorItens;

    /** Taxa cobrada do cliente. Nao confundir com o repasse ao entregador. */
    @PositiveOrZero
    @Column(name = "valor_taxa_entrega", precision = 10, scale = 2)
    private BigDecimal valorTaxaEntrega;

    @NotNull
    @PositiveOrZero
    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 30)
    private FormaPagamento formaPagamento;

    /** Informa ao entregador se ha valor a receber na porta. */
    @NotNull
    @Column(name = "pago_online", nullable = false)
    private Boolean pagoOnline = false;

    @PositiveOrZero
    @Column(name = "troco_para", precision = 10, scale = 2)
    private BigDecimal trocoPara;

    @Column(name = "observacoes", columnDefinition = "text")
    private String observacoes;

    /** Resposta original da API, para auditoria e reprocessamento (RNF13). */
    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    /** Momento em que a ENTREGA correspondente foi criada. */
    @Column(name = "processado_em")
    private LocalDateTime processadoEm;

    /**
     * Itens do pedido, para conferencia da sacola (UC02).
     *
     * Cascade e orphanRemoval porque o item nao existe sem o pedido:
     * ele nasce, e salvo e e removido junto com o pedido que o contem.
     */
    @OneToMany(
            mappedBy = "pedidoExterno",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<@Valid ItemPedido> itens = new ArrayList<>();

    /** Mantem os dois lados da relacao em sincronia. */
    public void adicionarItem(ItemPedido item) {
        this.itens.add(item);
        item.setPedidoExterno(this);
    }

    public void removerItem(ItemPedido item) {
        this.itens.remove(item);
        item.setPedidoExterno(null);
    }
}
