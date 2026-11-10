package br.com.agilizadelivery.service.rota;

import java.math.BigDecimal;
import java.util.Optional;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

/**
 * Decorator que reaproveita distancias ja calculadas entre o mesmo par
 * de coordenadas (mitigacao do risco R06). Apenas acertos sao guardados.
 */
public class RotasComCache implements ServicoDeRotas {

    private final ServicoDeRotas delegado;
    private final CacheLru<String, BigDecimal> cache;

    public RotasComCache(ServicoDeRotas delegado, int capacidade) {
        this.delegado = delegado;
        this.cache = new CacheLru<>(capacidade);
    }

    @Override
    public Optional<BigDecimal> distanciaKm(Coordenada origem, Coordenada destino) {
        if (origem == null || !origem.preenchida() || destino == null || !destino.preenchida()) {
            return delegado.distanciaKm(origem, destino);
        }
        String chave = chave(origem) + "|" + chave(destino);
        Optional<BigDecimal> guardada = cache.buscar(chave);
        if (guardada.isPresent()) {
            return guardada;
        }
        Optional<BigDecimal> calculada = delegado.distanciaKm(origem, destino);
        calculada.ifPresent(distancia -> cache.guardar(chave, distancia));
        return calculada;
    }

    private static String chave(Coordenada coordenada) {
        return coordenada.getLatitude().stripTrailingZeros().toPlainString()
                + "," + coordenada.getLongitude().stripTrailingZeros().toPlainString();
    }
}
