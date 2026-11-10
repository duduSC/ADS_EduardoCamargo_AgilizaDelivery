package br.com.agilizadelivery.service.rota;

import java.math.BigDecimal;
import java.util.Optional;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

/**
 * Porta de calculo da distancia viaria loja -> cliente (DVP 1.5.5,
 * passo 1).
 *
 * Os servicos dependem apenas desta interface; a implementacao concreta
 * (Google Routes API) pode ser trocada sem altera-los. Um resultado vazio
 * significa que a rota nao pode ser obtida, e a entrega fica pendente de
 * revisao com motivo SEM_DISTANCIA.
 */
public interface ServicoDeRotas {

    Optional<BigDecimal> distanciaKm(Coordenada origem, Coordenada destino);
}
