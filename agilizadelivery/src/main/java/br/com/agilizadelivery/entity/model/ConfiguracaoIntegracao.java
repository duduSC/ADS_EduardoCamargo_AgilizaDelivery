package br.com.agilizadelivery.entity.model;

import br.com.agilizadelivery.entity.model.base.EntidadeDoEstabelecimento;
import java.time.LocalDateTime;

import br.com.agilizadelivery.entity.model.enums.Provedor;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vinculo de um estabelecimento com a sua loja no provedor (DVP 1.5.4).
 *
 * Nao guarda credenciais. No modelo de integracao centralizada, o Agiliza
 * Delivery e uma unica aplicacao cadastrada no portal do provedor, e cada
 * restaurante a autoriza a acessar sua loja. Por isso client_id e
 * client_secret pertencem a aplicacao e ficam na configuracao do servidor
 * (variaveis de ambiente ou cofre de segredos), nunca no banco.
 *
 * Uma consulta de eventos atende todas as lojas, filtrando por merchantId.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "configuracao_integracao",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_configuracao_estabelecimento_provedor",
                        columnNames = {"estabelecimento_id", "provedor"}),
                @UniqueConstraint(
                        name = "uk_configuracao_provedor_merchant",
                        columnNames = {"provedor", "merchant_id"})
        })
public class ConfiguracaoIntegracao extends EntidadeDoEstabelecimento {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "provedor", nullable = false, length = 20)
    private Provedor provedor;

    /** Identificador da loja no provedor. */
    @NotBlank
    @Size(max = 100)
    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    /** Liga e desliga o polling desta loja. */
    @NotNull
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    /** Diagnostico da integracao: momento da ultima consulta bem-sucedida. */
    @Column(name = "ultimo_polling_em")
    private LocalDateTime ultimoPollingEm;
}
