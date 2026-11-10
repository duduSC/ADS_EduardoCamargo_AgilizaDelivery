package br.com.agilizadelivery.service.rota;

import java.util.Optional;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

/**
 * Porta de geocodificacao de enderecos (DVP UC01 A1).
 *
 * Um resultado vazio significa que o endereco nao foi geocodificado, e a
 * entrega fica pendente de revisao com motivo SEM_COORDENADA ate o
 * operador posicionar o pino manualmente.
 */
public interface ServicoDeGeocodificacao {

    Optional<Coordenada> geocodificar(String endereco);
}
