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

    /** O que a Google Maps Platform respondeu; falha nula quando ha distancia. */
    public record Posicao(Coordenada coordenada, BigDecimal distanciaKm, MotivoRevisao falha) {
    }

    /**
     * So as chamadas externas (ate 5 s cada): fora de transacao, para nao
     * prender conexao do pool nem a trava da loja (T014).
     */
    public Posicao consultar(Coordenada informada, String endereco, Coordenada origem) {
        Coordenada destino = informada;
        if (destino == null || !destino.preenchida()) {
            Optional<Coordenada> encontrada = geocodificacao.geocodificar(endereco);
            if (encontrada.isEmpty()) {
                return new Posicao(null, null, MotivoRevisao.SEM_COORDENADA);
            }
            destino = encontrada.get();
        }
        Optional<BigDecimal> distancia = rotas.distanciaKm(origem, destino);
        if (distancia.isEmpty()) {
            return new Posicao(destino, null, MotivoRevisao.SEM_DISTANCIA);
        }
        return new Posicao(destino, distancia.get(), null);
    }

    /** Grava a posicao na entrega, com a faixa vigente; dentro da transacao. */
    public void aplicar(Entrega entrega, Posicao posicao) {
        entrega.setCoordenada(posicao.coordenada());
        if (posicao.falha() != null) {
            marcarRevisao(entrega, posicao.falha());
            return;
        }
        entrega.setDistanciaKm(posicao.distanciaKm());
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
