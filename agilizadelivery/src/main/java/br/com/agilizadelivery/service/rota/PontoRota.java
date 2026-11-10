package br.com.agilizadelivery.service.rota;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

/**
 * Origem, parada ou destino de uma rota.
 *
 * Usa a coordenada quando ela esta completa e, na falta dela, o endereco
 * textual, que a API de rotas tambem aceita.
 */
public record PontoRota(Coordenada coordenada, String endereco) {

    public static PontoRota de(Estabelecimento estabelecimento) {
        return new PontoRota(estabelecimento.getCoordenada(), estabelecimento.getEnderecoCompleto());
    }

    public static PontoRota de(Entrega entrega) {
        return new PontoRota(entrega.getCoordenada(), entrega.getEnderecoCompleto());
    }

    public boolean temCoordenada() {
        return coordenada != null && coordenada.preenchida();
    }

    public boolean localizavel() {
        return temCoordenada() || (endereco != null && !endereco.isBlank());
    }
}
