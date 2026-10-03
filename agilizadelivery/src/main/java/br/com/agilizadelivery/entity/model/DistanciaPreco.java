package br.com.agilizadelivery.entity.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Faixa de preco por distancia, propria de cada loja (HU10).
 *
 * O intervalo e fechado no inicio e aberto no fim: de_km <= distancia < ate_km.
 *
 * Duas regras de integridade valem para as faixas ativas de um mesmo
 * estabelecimento (DVP 1.5.4):
 * - sem sobreposicao, garantida no banco por EXCLUDE USING gist;
 * - sem lacunas, validada na camada de servico, que grava a tabela
 *   inteira da loja em uma unica transacao.
 *
 * Faixas substituidas sao inativadas, nunca excluidas, para preservar a
 * auditoria de recibos antigos.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "distancia_preco")
public class DistanciaPreco extends EntidadeDoEstabelecimento {

    /** Inicio da faixa, inclusive. */
    @NotNull
    @PositiveOrZero
    @Column(name = "de_km", nullable = false, precision = 5, scale = 2)
    private BigDecimal deKm;

    /** Fim da faixa, exclusive. Deve ser maior que deKm. */
    @NotNull
    @Positive
    @Column(name = "ate_km", nullable = false, precision = 5, scale = 2)
    private BigDecimal ateKm;

    @NotNull
    @Positive
    @Column(name = "valor_pago", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorPago;

    @NotNull
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @NotNull
    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    /** Verdadeiro quando a distancia informada cai nesta faixa. */
    public boolean contem(BigDecimal distanciaKm) {
        if (distanciaKm == null || deKm == null || ateKm == null) {
            return false;
        }
        return distanciaKm.compareTo(deKm) >= 0 && distanciaKm.compareTo(ateKm) < 0;
    }
}
