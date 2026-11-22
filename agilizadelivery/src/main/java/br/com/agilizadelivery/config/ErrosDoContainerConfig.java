package br.com.agilizadelivery.config;

import org.apache.catalina.core.StandardHost;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.agilizadelivery.exception.handler.ValvulaDeErroJson;
import tools.jackson.databind.ObjectMapper;

/**
 * Erros do Tomcat em JSON. A valvula entra como instancia, e nao pelo nome
 * da classe, porque o Tomcat nao enxerga o classloader do devtools.
 */
@Configuration(proxyBeanMethods = false)
public class ErrosDoContainerConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> errosDoTomcatEmJson(ObjectMapper json) {
        return fabrica -> fabrica.addContextCustomizers(contexto -> {
            if (contexto.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass("");
                host.getPipeline().addValve(new ValvulaDeErroJson(json));
            }
        });
    }
}
