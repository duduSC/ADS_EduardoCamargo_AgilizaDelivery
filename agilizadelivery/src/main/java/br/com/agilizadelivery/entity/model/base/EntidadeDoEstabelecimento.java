package br.com.agilizadelivery.entity.model.base;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Classe-mae das entidades operacionais que pertencem a um estabelecimento.
 *
 * O vinculo obrigatorio com o estabelecimento e o que garante o isolamento
 * da multilocacao (DVP 1.2.1 e HU11): toda consulta operacional e filtrada
 * por esta coluna.
 *
 * Herdada por DistanciaPreco, ConfiguracaoIntegracao, PedidoExterno,
 * Entrega, LoteEntrega e Recibo.
 *
 * Usuario nao herda desta classe porque o perfil ADMIN_GERAL e global:
 * la a mesma FK existe, porem opcional.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class EntidadeDoEstabelecimento extends EntidadeBase {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private Estabelecimento estabelecimento;
}
