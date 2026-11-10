package br.com.agilizadelivery.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.agilizadelivery.seguranca.CpfProperties;

@Configuration
@EnableConfigurationProperties(CpfProperties.class)
public class SegurancaConfig {

    /** Fator de custo 10, o minimo exigido pelo RNF02. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
