package br.com.agilizadelivery.seguranca.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.agilizadelivery.exception.TokenInvalidoException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Le o cabecalho "Authorization: Bearer <jwt>" e, se o token de acesso for
 * valido, autentica a requisicao com o UsuarioLogado como principal e o
 * perfil como papel (ROLE_<perfil>).
 *
 * Token invalido nao interrompe aqui: a requisicao segue sem autenticacao
 * e o ponto de entrada responde 401 se a rota exigir login. Nada do token
 * e logado.
 */
public class FiltroAutenticacaoJwt extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final ServicoJwt servicoJwt;

    public FiltroAutenticacaoJwt(ServicoJwt servicoJwt) {
        this.servicoJwt = servicoJwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho != null && cabecalho.regionMatches(true, 0, PREFIXO, 0, PREFIXO.length())) {
            try {
                UsuarioLogado usuario = servicoJwt.validarTokenAcesso(cabecalho.substring(PREFIXO.length()));
                SecurityContextHolder.getContext().setAuthentication(autenticacao(usuario));
            } catch (TokenInvalidoException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    public static UsernamePasswordAuthenticationToken autenticacao(UsuarioLogado usuario) {
        return UsernamePasswordAuthenticationToken.authenticated(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.perfil().name())));
    }
}
