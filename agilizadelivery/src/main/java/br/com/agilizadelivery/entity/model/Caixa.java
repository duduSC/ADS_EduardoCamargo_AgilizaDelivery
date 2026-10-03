package br.com.agilizadelivery.entity.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Periodo de operacao de uma loja, aberto no inicio do expediente e
 * fechado ao final dele.
 *
 * E o caixa, e nao o status do recibo, que determina ate quando os
 * valores de um dia podem ser corrigidos. Divergencias costumam
 * aparecer minutos depois do pagamento, ao conferir com outro
 * entregador, e nesse momento o acerto ainda precisa ser corrigivel.
 * Fechado o caixa, o periodo inteiro torna-se imutavel.
 *
 * Ha no maximo um caixa por loja em cada dia.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "caixa",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_caixa_estabelecimento_data",
                columnNames = {"estabelecimento_id", "data_referencia"}))
public class Caixa extends EntidadeDoEstabelecimento {

    /** Dia de operacao. Unico por estabelecimento. */
    @NotNull
    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @NotNull
    @Column(name = "aberto_em", nullable = false)
    private LocalDateTime abertoEm;

    /** Nulo enquanto o caixa nao foi fechado. */
    @Column(name = "fechado_em")
    private LocalDateTime fechadoEm;

    /** Operador ou administrador que fechou o expediente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_fechamento_id")
    private Usuario usuarioFechamento;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private StatusCaixa status = StatusCaixa.ABERTO;

    /** Consolidados gravados no fechamento. */
    @PositiveOrZero
    @Column(name = "qtd_recibos")
    private Short qtdRecibos;

    @PositiveOrZero
    @Column(name = "valor_total", precision = 12, scale = 2)
    private BigDecimal valorTotal;

    /** Reabertura, privativa do Administrador do Restaurante. */
    @Column(name = "reaberto_em")
    private LocalDateTime reabertoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_reabertura_id")
    private Usuario usuarioReabertura;

    @Size(max = 255)
    @Column(name = "motivo_reabertura", length = 255)
    private String motivoReabertura;

    /**
     * Enquanto aberto, os recibos do periodo aceitam correcao, mesmo os
     * ja pagos. E a unica condicao que libera alteracao de valores.
     */
    public boolean estaAberto() {
        return StatusCaixa.ABERTO.equals(this.status)
                || StatusCaixa.REABERTO.equals(this.status);
    }

    /** Somente um caixa fechado pode ser reaberto. */
    public boolean podeSerReaberto() {
        return StatusCaixa.FECHADO.equals(this.status);
    }
}
