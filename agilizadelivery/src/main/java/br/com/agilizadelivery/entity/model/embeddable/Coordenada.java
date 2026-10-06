package br.com.agilizadelivery.entity.model.embeddable;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Par latitude/longitude, usado como tipo-valor.
 *
 * @Embeddable nao gera tabela nem chave primaria: as duas colunas sao
 * gravadas direto na tabela de quem usa a classe. Nao ha FK nem join.
 *
 * Reune em um unico lugar a precisao DECIMAL(10,7) do modelo logico e as
 * faixas validas de cada eixo, evitando repetir isso em Estabelecimento,
 * Motoboy, Entrega e EntregaStatusHistorico.
 *
 * Onde o nome da coluna difere, como a ultima posicao conhecida do
 * entregador, o uso aplica @AttributeOverride.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class Coordenada {

    @DecimalMin(value = "-90.0", message = "Latitude deve estar entre -90 e 90")
    @DecimalMax(value = "90.0", message = "Latitude deve estar entre -90 e 90")
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Longitude deve estar entre -180 e 180")
    @DecimalMax(value = "180.0", message = "Longitude deve estar entre -180 e 180")
    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * Indica se o par esta completo. Uma entrega sem coordenada fica
     * pendente de revisao com motivo SEM_COORDENADA (UC01 A1).
     */
    public boolean preenchida() {
        return latitude != null && longitude != null;
    }
}
