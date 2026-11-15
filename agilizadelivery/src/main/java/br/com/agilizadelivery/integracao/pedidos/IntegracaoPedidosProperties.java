package br.com.agilizadelivery.integracao.pedidos;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros do worker de integracao e do simulador (DVP 1.5.6, RNF04,
 * T011).
 *
 * worker.habilitado liga o agendamento; worker.intervalo e o ciclo de
 * polling (30 s no DVP); worker.max-tentativas e worker.recuo-inicial
 * regem a repeticao com recuo exponencial do RNF04.
 *
 * simulador: quantos pedidos no maximo por ciclo, o raio em torno da loja
 * e o limite de entregas AGUARDANDO_DESPACHO a partir do qual ele para
 * de gerar (para nao inundar o mapa).
 */
@ConfigurationProperties(prefix = "agiliza.integracao")
public record IntegracaoPedidosProperties(Worker worker, Simulador simulador) {

    public IntegracaoPedidosProperties {
        if (worker == null) {
            worker = new Worker(false, null, null, null);
        }
        if (simulador == null) {
            simulador = new Simulador(null, null, null);
        }
    }

    public record Worker(boolean habilitado, Duration intervalo, Duration recuoInicial, Integer maxTentativas) {

        public Worker {
            if (intervalo == null) {
                intervalo = Duration.ofSeconds(30);
            }
            if (recuoInicial == null) {
                recuoInicial = Duration.ofMillis(200);
            }
            if (maxTentativas == null || maxTentativas < 1) {
                maxTentativas = 5;
            }
        }
    }

    public record Simulador(Integer maxPorCiclo, Double raioKm, Integer limiteAguardando) {

        public Simulador {
            if (maxPorCiclo == null || maxPorCiclo < 0) {
                maxPorCiclo = 2;
            }
            if (raioKm == null || raioKm <= 0) {
                raioKm = 6.0;
            }
            if (limiteAguardando == null || limiteAguardando < 1) {
                limiteAguardando = 15;
            }
        }
    }
}
