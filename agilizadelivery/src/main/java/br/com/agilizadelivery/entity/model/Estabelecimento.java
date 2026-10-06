package br.com.agilizadelivery.entity.model;

import java.math.BigDecimal;

import br.com.agilizadelivery.entity.model.base.EntidadeBase;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.br.CNPJ;

/**
 * Restaurante parceiro. Raiz da multilocacao (DVP 1.5.4).
 *
 * A coordenada da loja e a origem de todos os calculos de distancia
 * das entregas (DVP 1.5.5).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "estabelecimento")
public class Estabelecimento extends EntidadeBase {

    @NotBlank
    @Size(max = 150)
    @Column(name = "razao_social", nullable = false, length = 150)
    private String razaoSocial;

    @NotBlank
    @Size(max = 100)
    @Column(name = "nome_fantasia", nullable = false, length = 100)
    private String nomeFantasia;

    /** Somente digitos. Unico no sistema (HU11). */
    @CNPJ
    @Column(name = "cnpj", nullable = false, unique = true, length = 14)
    private String cnpj;

    @Size(max = 15)
    @Column(name = "telefone", length = 15)
    private String telefone;

    @NotBlank
    @Size(max = 255)
    @Column(name = "endereco_completo", nullable = false)
    private String enderecoCompleto;

    @Valid
    @Embedded
    private Coordenada coordenada;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private StatusEstabelecimento status = StatusEstabelecimento.ATIVO;

    /** Limite de pedidos por lote, parametrizavel pela loja (HU02). */
    @NotNull
    @Positive
    @Column(name = "max_pedidos_por_lote", nullable = false)
    private Short maxPedidosPorLote = 5;

    /**
     * Repasse aplicado quando a distancia ultrapassa a ultima faixa da
     * loja, ou seja, quando o cliente esta fora do raio de entrega
     * (DVP 1.5.5, passo 3).
     *
     * Nesse caso a entrega fica com faixa_preco_id nulo e segue o fluxo
     * normalmente, sem pendencia de revisao.
     */
    @PositiveOrZero
    @Column(name = "valor_repasse_fora_de_area", precision = 10, scale = 2)
    private BigDecimal valorRepasseForaDeArea;
}
