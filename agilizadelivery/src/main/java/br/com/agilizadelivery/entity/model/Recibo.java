package br.com.agilizadelivery.entity.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
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
 * Comprovante do acerto financeiro (HU07).
 *
 * A data de geracao e o criadoEm herdado: o recibo nasce GERADO.
 *
 * Uma vez vinculadas ao recibo, as entregas tem o valor congelado e nao
 * podem entrar em outro recibo. Cancelar o recibo (UC03 A2) desvincula as
 * entregas e as libera para novo acerto; recibos PAGOS nao sao cancelaveis.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recibo")
public class Recibo extends EntidadeDoEstabelecimento {

    /** Beneficiario do repasse. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "motoboy_id", nullable = false)
    private Motoboy motoboy;

    /** Operador que fechou o acerto. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_gerador_id", nullable = false)
    private Usuario usuarioGerador;

    @NotNull
    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @NotNull
    @Column(name = "periodo_inicio", nullable = false)
    private LocalDateTime periodoInicio;

    @NotNull
    @Column(name = "periodo_fim", nullable = false)
    private LocalDateTime periodoFim;

    @NotNull
    @Positive
    @Column(name = "qtd_entregas", nullable = false)
    private Short qtdEntregas;

    /** Soma dos repasses das entregas consolidadas (DVP 1.5.5, passo 8). */
    @NotNull
    @PositiveOrZero
    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private StatusRecibo status = StatusRecibo.GERADO;

    /** Confirmacao do PIX pelo operador (UC03, passo 6). */
    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    /** Somente recibos ainda nao pagos podem ser cancelados (UC03 A2). */
    public boolean podeSerCancelado() {
        return StatusRecibo.GERADO.equals(this.status);
    }
}
