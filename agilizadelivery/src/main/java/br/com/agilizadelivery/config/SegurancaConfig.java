package br.com.agilizadelivery.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import br.com.agilizadelivery.seguranca.CorsProperties;
import br.com.agilizadelivery.seguranca.CpfProperties;
import br.com.agilizadelivery.seguranca.RespostaErroSeguranca;
import br.com.agilizadelivery.seguranca.jwt.FiltroAutenticacaoJwt;
import br.com.agilizadelivery.seguranca.jwt.JwtProperties;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;

/**
 * API stateless autenticada por JWT (DVP 1.5.1).
 *
 * Publicos: login, refresh e o health do actuator. Todo o resto exige
 * token de acesso valido; a autorizacao por perfil fica nos controllers
 * (@PreAuthorize com as expressoes de Perfis).
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({ CpfProperties.class, JwtProperties.class, CorsProperties.class })
public class SegurancaConfig {

    /** Fator de custo 10, o minimo exigido pelo RNF02. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ServicoJwt servicoJwt,
            RespostaErroSeguranca respostaErro,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(respostaErro)
                        .accessDeniedHandler(respostaErro))
                .addFilterBefore(new FiltroAutenticacaoJwt(servicoJwt), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(properties.origensPermitidas());
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuracao.setAllowCredentials(false);
        configuracao.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", configuracao);
        return fonte;
    }

    /**
     * Impede o usuario em memoria com senha gerada que o Spring Boot cria
     * quando nao ha UserDetailsService. A autenticacao e so por JWT.
     */
    @Bean
    public UserDetailsService semUsuariosEmMemoria() {
        return username -> {
            throw new UsernameNotFoundException("Autenticação apenas por token.");
        };
    }
}
