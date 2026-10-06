package br.com.agilizadelivery.entity.model;

import java.time.LocalDateTime;

import br.com.agilizadelivery.entity.model.enums.EtapaFalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.StatusFalhaIntegracao;
import jakarta.persistence.Column;
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
 * Fila de erro da integracao (RNF04).
 *
 * Apos cinco tentativas com recuo exponencial, a falha e registrada aqui
 * e o painel exibe o alerta de integracao degradada, sem interromper o
 * ciclo de polling.
 *
 * Tabela append-only com PK bigint: nao herda EntidadeBase.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "falha_integracao")
public class FalhaIntegracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /** Integracao, e por ela a loja, em que a falha ocorreu. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "configuracao_integracao_id", nullable = false)
    private ConfiguracaoIntegracao configuracaoIntegracao;

    /** Pedido afetado, quando identificavel. */
    @Size(max = 100)
    @Column(name = "id_externo", length = 100)
    private String idExterno;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "etapa", nullable = false, length = 20)
    private EtapaFalhaIntegracao etapa;

    @NotBlank
    @Column(name = "mensagem_erro", nullable = false, columnDefinition = "text")
    private String mensagemErro;

    /** Numero de tentativas realizadas, no maximo cinco (RNF04). */
    @NotNull
    @PositiveOrZero
    @Column(name = "tentativas", nullable = false)
    private Short tentativas;

    /** Resposta recebida, quando houver, para reprocessamento. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json")
    private String payloadJson;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private StatusFalhaIntegracao status = StatusFalhaIntegracao.PENDENTE;

    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private LocalDateTime ocorridoEm;

    @Column(name = "resolvido_em")
    private LocalDateTime resolvidoEm;

    @PrePersist
    protected void aoCriar() {
        if (this.ocorridoEm == null) {
            this.ocorridoEm = LocalDateTime.now();
        }
    }
}
