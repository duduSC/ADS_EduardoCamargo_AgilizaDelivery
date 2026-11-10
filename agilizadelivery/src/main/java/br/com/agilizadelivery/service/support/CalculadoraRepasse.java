package br.com.agilizadelivery.service.support;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.agilizadelivery.entity.model.DistanciaPreco;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.repository.DistanciaPrecoRepository;

/**
 * Regra de precificacao do repasse (DVP 1.5.5).
 *
 * O repasse e apurado por entrega, pela distancia loja -> cliente. A
 * ordem das paradas no lote nao altera o valor.
 */
@Component
public class CalculadoraRepasse {

    private final DistanciaPrecoRepository distanciaPrecoRepository;

    public CalculadoraRepasse(DistanciaPrecoRepository distanciaPrecoRepository) {
        this.distanciaPrecoRepository = distanciaPrecoRepository;
    }

    /**
     * Aplica a faixa ativa no momento (passos 2 a 4). Serve tanto para o
     * valor provisorio quanto para o congelamento no estado final.
     *
     * Acima da ultima faixa vale o repasse de fora de area da loja, com
     * faixa nula. Sem distancia conhecida, nada e alterado.
     */
    public void aplicarFaixaVigente(Entrega entrega) {
        BigDecimal distanciaKm = entrega.getDistanciaKm();
        if (distanciaKm == null) {
            return;
        }

        List<DistanciaPreco> faixas = distanciaPrecoRepository
                .findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(entrega.getEstabelecimento().getId());

        DistanciaPreco faixa = faixas.stream()
                .filter(candidata -> candidata.contem(distanciaKm))
                .findFirst()
                .orElse(null);

        if (faixa != null) {
            entrega.setFaixaPreco(faixa);
            entrega.setValorRepasse(faixa.getValorPago());
            return;
        }

        entrega.setFaixaPreco(null);
        entrega.setValorRepasse(entrega.getEstabelecimento().getValorRepasseForaDeArea());
    }

    /** Cancelamento antes da saida da loja: nao houve deslocamento (passo 5). */
    public void zerarRepasse(Entrega entrega) {
        entrega.setFaixaPreco(null);
        entrega.setValorRepasse(BigDecimal.ZERO);
    }
}
