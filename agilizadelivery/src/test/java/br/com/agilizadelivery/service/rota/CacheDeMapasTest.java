package br.com.agilizadelivery.service.rota;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

/** Decorators de cache da geocodificacao e das rotas (risco R06). */
@ExtendWith(MockitoExtension.class)
class CacheDeMapasTest {

    @Mock
    private ServicoDeGeocodificacao geocodificacao;

    @Mock
    private ServicoDeRotas rotas;

    private final Coordenada loja = new Coordenada(reais("-23.5505200"), reais("-46.6333080"));
    private final Coordenada cliente = new Coordenada(reais("-23.5614000"), reais("-46.6559000"));

    @Test
    @DisplayName("Deve reaproveitar a coordenada de endereço já geocodificado, ignorando acentos e espaços")
    void deveReaproveitarGeocodificacao() {
        GeocodificacaoComCache comCache = new GeocodificacaoComCache(geocodificacao, 10);
        when(geocodificacao.geocodificar("Av. São João, 100")).thenReturn(Optional.of(cliente));

        Coordenada primeira = comCache.geocodificar("Av. São João, 100").orElseThrow();
        Coordenada segunda = comCache.geocodificar("  av. sao   JOÃO, 100 ").orElseThrow();

        verify(geocodificacao, times(1)).geocodificar(any());
        assertEquals(primeira.getLatitude(), segunda.getLatitude());
        assertNotSame(primeira, segunda);
    }

    @Test
    @DisplayName("Não deve guardar falhas de geocodificação, para tentar de novo depois")
    void naoDeveGuardarFalhaDeGeocodificacao() {
        GeocodificacaoComCache comCache = new GeocodificacaoComCache(geocodificacao, 10);
        when(geocodificacao.geocodificar("Rua X")).thenReturn(Optional.empty());

        assertTrue(comCache.geocodificar("Rua X").isEmpty());
        assertTrue(comCache.geocodificar("Rua X").isEmpty());
        assertTrue(comCache.geocodificar(" ").isEmpty());

        verify(geocodificacao, times(2)).geocodificar("Rua X");
    }

    @Test
    @DisplayName("Deve reaproveitar a distância do mesmo par de coordenadas, mesmo com escala diferente")
    void deveReaproveitarDistancia() {
        RotasComCache comCache = new RotasComCache(rotas, 10);
        when(rotas.distanciaKm(any(), any())).thenReturn(Optional.of(reais("2.10")));

        comCache.distanciaKm(loja, cliente);
        Optional<BigDecimal> repetida = comCache.distanciaKm(
                loja, new Coordenada(reais("-23.5614"), reais("-46.6559")));

        assertEquals(Optional.of(reais("2.10")), repetida);
        verify(rotas, times(1)).distanciaKm(any(), any());
    }

    @Test
    @DisplayName("Deve descartar a entrada mais antiga quando o cache está cheio")
    void deveRespeitarCapacidade() {
        CacheLru<String, Integer> cache = new CacheLru<>(2);
        cache.guardar("a", 1);
        cache.guardar("b", 2);
        cache.buscar("a");
        cache.guardar("c", 3);

        assertEquals(2, cache.tamanho());
        assertTrue(cache.buscar("a").isPresent());
        assertTrue(cache.buscar("b").isEmpty());
    }
}
