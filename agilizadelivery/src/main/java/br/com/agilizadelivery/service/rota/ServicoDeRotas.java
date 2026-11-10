package br.com.agilizadelivery.service.rota;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Porta de calculo de distancias viarias (DVP 1.5.5, passo 1).
 *
 * Os servicos dependem apenas desta interface; a implementacao concreta
 * (Google Routes API) pode ser trocada sem altera-los. Um resultado vazio
 * significa que a rota nao pode ser obtida, e a entrega fica pendente de
 * revisao com motivo SEM_DISTANCIA.
 */
public interface ServicoDeRotas {

    /** Distancia viaria de um ponto a outro, em km. */
    Optional<BigDecimal> distanciaKm(PontoRota origem, PontoRota destino);

    /**
     * Rota que parte da origem e percorre as paradas na ordem informada,
     * terminando na ultima delas.
     */
    Optional<RotaCalculada> calcularRota(PontoRota origem, List<PontoRota> paradas);
}
