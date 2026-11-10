package br.com.agilizadelivery.entity.model;

import br.com.agilizadelivery.entity.model.base.EntidadeDoEstabelecimento;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import br.com.agilizadelivery.entity.model.enums.StatusLote;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agrupamento de entregas despachado a um entregador (DVP 1.5.4).
 *
 * O lote nasce DESPACHADO, pois e criado no ato do despacho (UC01).
 *
 * O limite de entregas por lote vem de Estabelecimento.maxPedidosPorLote
 * (HU02). Essa regra nao pode virar um CHECK, porque a restricao
 * precisaria consultar outra tabela: e validada na camada de servico e
 * reforcada por trigger.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lote_entrega")
public class LoteEntrega extends EntidadeDoEstabelecimento {

    /** Entregador que executa o lote. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "motoboy_id", nullable = false)
    private Motoboy motoboy;

    /** Operador que confirmou o despacho. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_despachante_id", nullable = false)
    private Usuario usuarioDespachante;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private StatusLote status = StatusLote.DESPACHADO;

    @NotNull
    @Positive
    @Column(name = "qtd_entregas", nullable = false)
    private Short qtdEntregas;

    /** Soma do valorRepasse das entregas do lote (DVP 1.5.5, passo 7). */
    @PositiveOrZero
    @Column(name = "valor_total_repasse", precision = 10, scale = 2)
    private BigDecimal valorTotalRepasse;

    @Column(name = "despachado_em")
    private LocalDateTime despachadoEm;

    @Column(name = "concluido_em")
    private LocalDateTime concluidoEm;
}
