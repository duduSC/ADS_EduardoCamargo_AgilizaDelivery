package br.com.agilizadelivery.service.rota;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

/**
 * Decorator que reaproveita a coordenada de enderecos ja geocodificados,
 * pelo endereco normalizado (mitigacao do risco R06).
 *
 * Apenas acertos sao guardados: uma falha volta a ser consultada na
 * proxima vez, pois pode ter sido uma indisponibilidade momentanea.
 */
public class GeocodificacaoComCache implements ServicoDeGeocodificacao {

    private final ServicoDeGeocodificacao delegado;
    private final CacheLru<String, Coordenada> cache;

    public GeocodificacaoComCache(ServicoDeGeocodificacao delegado, int capacidade) {
        this.delegado = delegado;
        this.cache = new CacheLru<>(capacidade);
    }

    @Override
    public Optional<Coordenada> geocodificar(String endereco) {
        String chave = normalizar(endereco);
        if (chave.isEmpty()) {
            return Optional.empty();
        }
        Optional<Coordenada> guardada = cache.buscar(chave);
        if (guardada.isPresent()) {
            return guardada.map(GeocodificacaoComCache::copia);
        }
        Optional<Coordenada> encontrada = delegado.geocodificar(endereco);
        encontrada.ifPresent(coordenada -> cache.guardar(chave, copia(coordenada)));
        return encontrada;
    }

    /** Sem acentos, em minusculas e com espacos simples. */
    static String normalizar(String endereco) {
        if (endereco == null) {
            return "";
        }
        return Normalizer.normalize(endereco, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Coordenada e mutavel: cada entrega recebe a sua instancia. */
    private static Coordenada copia(Coordenada coordenada) {
        return new Coordenada(coordenada.getLatitude(), coordenada.getLongitude());
    }
}
