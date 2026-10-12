package br.com.agilizadelivery.entity.model;

import br.com.agilizadelivery.entity.model.base.EntidadeDoEstabelecimento;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import br.com.agilizadelivery.entity.model.enums.FormaPagamento;
import br.com.agilizadelivery.entity.model.enums.MotivoRevisao;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unidade logistica e de cobranca do repasse (DVP 1.5.4).
 *
 * O repasse e apurado por entrega, nunca por lote: o agrupamento e uma
 * otimizacao logistica e nao altera o valor devido (DVP 1.5.5).
 *
 * A entidade guarda uma copia operacional dos dados do cliente e do
 * pagamento, de modo a ser autossuficiente tanto para pedidos importados
 * (copiados de PedidoExterno) quanto para entregas avulsas, digitadas
 * pelo operador (UC01 A2).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "entrega")
public class Entrega extends EntidadeDoEstabelecimento {

    /** Nulo quando a entrega e avulsa, cadastrada manualmente (UC01 A2). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_externo_id")
    private PedidoExterno pedidoExterno;

    /** Nulo enquanto AGUARDANDO_DESPACHO. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private LoteEntrega lote;

    /** Faixa aplicada; congelada quando a entrega atinge estado final. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faixa_preco_id")
    private DistanciaPreco faixaPreco;

    /** Preenchido no acerto; quando nao nulo, o valor e imutavel (HU07). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recibo_id")
    private Recibo recibo;

    @NotBlank
    @Size(max = 120)
    @Column(name = "nome_cliente", nullable = false, length = 120)
    private String nomeCliente;

    /** Base do deep link de WhatsApp (HU04). */
    @Size(max = 20)
    @Column(name = "telefone_cliente", length = 20)
    private String telefoneCliente;

    @NotBlank
    @Size(max = 255)
    @Column(name = "endereco_completo", nullable = false)
    private String enderecoCompleto;

    @Size(max = 100)
    @Column(name = "bairro", length = 100)
    private String bairro;

    @Size(max = 9)
    @Column(name = "cep", length = 9)
    private String cep;

    @Size(max = 100)
    @Column(name = "complemento", length = 100)
    private String complemento;

    @Size(max = 150)
    @Column(name = "ponto_referencia", length = 150)
    private String pontoReferencia;

    /**
     * Coordenada efetivamente usada, ja com eventual ajuste manual do
     * pino pelo operador (risco R04). Nula apenas enquanto a entrega
     * estiver pendente de revisao por falha de geocodificacao.
     */
    @Valid
    @Embedded
    private Coordenada coordenada;

    @PositiveOrZero
    @Column(name = "valor_pedido", precision = 10, scale = 2)
    private BigDecimal valorPedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 30)
    private FormaPagamento formaPagamento;

    @NotNull
    @Column(name = "pago_online", nullable = false)
    private Boolean pagoOnline = false;

    @PositiveOrZero
    @Column(name = "troco_para", precision = 10, scale = 2)
    private BigDecimal trocoPara;

    @Column(name = "observacoes", columnDefinition = "text")
    private String observacoes;

    /** Distancia de rota loja -> cliente, obtida pela API de rotas. */
    @PositiveOrZero
    @Column(name = "distancia_km", precision = 6, scale = 2)
    private BigDecimal distanciaKm;

    /**
     * Valor devido ao entregador por esta entrega. Provisorio ate o
     * estado final, quando a faixa vigente e reaplicada e congelada
     * (DVP 1.5.5, passo 4).
     */
    @PositiveOrZero
    @Column(name = "valor_repasse", precision = 10, scale = 2)
    private BigDecimal valorRepasse;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private StatusEntrega status = StatusEntrega.AGUARDANDO_DESPACHO;

    /** Obrigatorio quando status = FALHA (UC02 A1). */
    @Size(max = 100)
    @Column(name = "motivo_falha", length = 100)
    private String motivoFalha;

    /** Bloqueia a inclusao em recibo ate a revisao do operador. */
    @NotNull
    @Column(name = "pendente_revisao", nullable = false)
    private Boolean pendenteRevisao = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_revisao", length = 30)
    private MotivoRevisao motivoRevisao;

    /**
     * Momento em que a cozinha concluiu o pedido, marcado pelo operador
     * (UC01, passo 5). Junto com despachadaEm, mede quanto tempo o pedido
     * aguardou na estufa, que e a metrica de aceleracao do despacho
     * (DVP 1.2.3).
     *
     * Fica nulo quando o despacho e imediato e o operador nao chega a
     * marcar o pedido como pronto (UC01 A5).
     */
    @Column(name = "pronto_para_despacho_em")
    private LocalDateTime prontoParaDespachoEm;

    @Column(name = "despachada_em")
    private LocalDateTime despachadaEm;

    @Column(name = "finalizada_em")
    private LocalDateTime finalizadaEm;

    /** ENTREGUE, FALHA e CANCELADA sao estados finais (DVP 1.4.4). */
    public boolean emEstadoFinal() {
        return StatusEntrega.ENTREGUE.equals(this.status)
                || StatusEntrega.FALHA.equals(this.status)
                || StatusEntrega.CANCELADA.equals(this.status);
    }

    /**
     * Uma entrega so entra em recibo se estiver em estado final, sem
     * revisao pendente e ainda nao vinculada (DVP 1.5.5, passo 8).
     */
    public boolean podeEntrarEmRecibo() {
        return emEstadoFinal() && !Boolean.TRUE.equals(this.pendenteRevisao) && this.recibo == null;
    }

    /**
     * O valor pode ser ajustado enquanto a entrega nao entrou em recibo
     * ou, tendo entrado, enquanto o caixa daquele dia segue aberto
     * (UC03 A1). O fechamento do caixa e que torna o valor imutavel.
     */
    public boolean permiteAjusteDeValor() {
        return this.recibo == null || this.recibo.permiteAlteracao();
    }

    /**
     * O entregador pode contestar o valor de uma entrega concluida
     * enquanto ela ainda for corrigivel, isto e, ate o fechamento do
     * caixa (UC08). Contestar uma entrega ja em revisao nao tem efeito.
     */
    public boolean podeSerContestada() {
        return emEstadoFinal()
                && !Boolean.TRUE.equals(this.pendenteRevisao)
                && permiteAjusteDeValor();
    }

    /**
     * Marca a entrega para revisao a pedido do entregador. Ela sai do
     * calculo do acerto ate que o operador a analise (DVP 1.5.5, passo 8).
     */
    public void contestar() {
        this.pendenteRevisao = true;
        this.motivoRevisao = MotivoRevisao.CONTESTACAO_ENTREGADOR;
    }
}
