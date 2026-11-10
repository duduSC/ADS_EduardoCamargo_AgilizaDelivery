package br.com.agilizadelivery.service.support;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Component;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.MotivoRevisao;
import br.com.agilizadelivery.service.rota.ServicoDeGeocodificacao;
import br.com.agilizadelivery.service.rota.ServicoDeRotas;

/**
 * Posiciona a entrega e calcula o repasse provisorio (DVP 1.5.5, passos
 * 1 a 3).
 *
 * pendente_revisao so e marcada por falha da Google Maps Platform:
 * SEM_COORDENADA quando o endereco nao e geocodificado e SEM_DISTANCIA
 * quando a rota nao e obtida. Distancia acima da ultima faixa nao gera
 * revisao: vale o repasse de fora de area da loja.
 */
@Component
public class LocalizacaoEntrega {

    private final ServicoDeGeocodificacao geocodificacao;
    private final ServicoDeRotas rotas;
    private final CalculadoraRepasse calculadoraRepasse;

    public LocalizacaoEntrega(
            ServicoDeGeocodificacao geocodificacao,
            ServicoDeRotas rotas,
            CalculadoraRepasse calculadoraRepasse) {
        this.geocodificacao = geocodificacao;
        this.rotas = rotas;
        this.calculadoraRepasse = calculadoraRepasse;
    }

    public void localizar(Entrega entrega) {
        if (entrega.getCoordenada() == null || !entrega.getCoordenada().preenchida()) {
            Optional<Coordenada> encontrada = geocodificacao.geocodificar(entrega.getEnderecoCompleto());
            if (encontrada.isEmpty()) {
                entrega.setCoordenada(null);
                marcarRevisao(entrega, MotivoRevisao.SEM_COORDENADA);
                return;
            }
            entrega.setCoordenada(encontrada.get());
        }

        Optional<BigDecimal> distancia = rotas.distanciaKm(
                entrega.getEstabelecimento().getCoordenada(),
                entrega.getCoordenada());
        if (distancia.isEmpty()) {
            marcarRevisao(entrega, MotivoRevisao.SEM_DISTANCIA);
            return;
        }

        entrega.setDistanciaKm(distancia.get());
        calculadoraRepasse.aplicarFaixaVigente(entrega);
        limparRevisaoDeLocalizacao(entrega);
    }

    private static void marcarRevisao(Entrega entrega, MotivoRevisao motivo) {
        entrega.setDistanciaKm(null);
        entrega.setFaixaPreco(null);
        entrega.setValorRepasse(null);
        entrega.setPendenteRevisao(true);
        entrega.setMotivoRevisao(motivo);
    }

    /** A contestacao do entregador nao e resolvida pelo reposicionamento. */
    private static void limparRevisaoDeLocalizacao(Entrega entrega) {
        if (entrega.getMotivoRevisao() == null
                || entrega.getMotivoRevisao() == MotivoRevisao.SEM_COORDENADA
                || entrega.getMotivoRevisao() == MotivoRevisao.SEM_DISTANCIA) {
            entrega.setPendenteRevisao(false);
            entrega.setMotivoRevisao(null);
        }
    }
}
