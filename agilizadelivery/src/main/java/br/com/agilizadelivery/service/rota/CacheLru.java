package br.com.agilizadelivery.service.rota;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cache em memoria de tamanho limitado que descarta o item usado ha mais
 * tempo. Seguro para uso concorrente.
 */
final class CacheLru<K, V> {

    private final Map<K, V> itens;

    CacheLru(int capacidade) {
        this.itens = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> maisAntigo) {
                return size() > capacidade;
            }
        };
    }

    synchronized Optional<V> buscar(K chave) {
        return Optional.ofNullable(itens.get(chave));
    }

    synchronized void guardar(K chave, V valor) {
        itens.put(chave, valor);
    }

    synchronized int tamanho() {
        return itens.size();
    }
}
