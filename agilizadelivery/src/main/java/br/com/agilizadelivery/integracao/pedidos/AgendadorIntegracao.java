package br.com.agilizadelivery.integracao.pedidos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Agenda o ciclo do worker de integracao a cada agiliza.integracao.worker.intervalo
 * (30 s no DVP 1.5.6). So existe com agiliza.integracao.worker.habilitado=true
 * (ligado no perfil h2); nos testes fica desligado e o ciclo e chamado direto.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "agiliza.integracao.worker", name = "habilitado", havingValue = "true")
public class AgendadorIntegracao {

    private final CicloIntegracao ciclo;

    public AgendadorIntegracao(CicloIntegracao ciclo) {
        this.ciclo = ciclo;
    }

    @Scheduled(
            initialDelayString = "${agiliza.integracao.worker.intervalo:30s}",
            fixedDelayString = "${agiliza.integracao.worker.intervalo:30s}")
    public void executar() {
        ciclo.executar();
    }
}
