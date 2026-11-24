package br.com.agilizadelivery.seguranca.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.exception.TokenInvalidoException;
import br.com.agilizadelivery.service.CanalAcesso;

/**
 * Testes unitarios do ServicoJwt: emissao, validacao, expiracao e
 * adulteracao de tokens de acesso e refresh (D002, Constituicao 6.1).
 */
class ServicoJwtTest {

    /** Base64 de 32 bytes, suficiente para HS256. */
    private static final String SEGREDO = Base64.getEncoder()
            .encodeToString("chave-de-teste-com-32-bytes!!!!!".getBytes());

    private static final Duration ACCESS_TTL = Duration.ofHours(3);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    private Clock clock;
    private ServicoJwt servico;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
        JwtProperties properties = new JwtProperties(SEGREDO, ACCESS_TTL, REFRESH_TTL, "agilizadelivery");
        servico = new ServicoJwt(properties, clock);
    }

    private UsuarioAutenticadoResponse operador() {
        return new UsuarioAutenticadoResponse(
                UUID.randomUUID(), UUID.randomUUID(), null, "Operador", PerfilUsuario.OPERADOR);
    }

    private UsuarioAutenticadoResponse adminGeral() {
        return new UsuarioAutenticadoResponse(
                UUID.randomUUID(), null, null, "Admin", PerfilUsuario.ADMIN_GERAL);
    }

    @Nested
    @DisplayName("Token de acesso")
    class TokenDeAcesso {

        @Test
        @DisplayName("Deve gerar e validar token de acesso com todos os claims (D002)")
        void deveGerarEValidarTokenAcesso() {
            UsuarioAutenticadoResponse usuario = operador();
            String token = servico.gerarTokenAcesso(usuario, CanalAcesso.BACKOFFICE);

            UsuarioLogado logado = servico.validarTokenAcesso(token);

            assertEquals(usuario.usuarioId(), logado.usuarioId());
            assertEquals(usuario.estabelecimentoId(), logado.estabelecimentoId());
            assertEquals(PerfilUsuario.OPERADOR, logado.perfil());
            assertEquals(CanalAcesso.BACKOFFICE, logado.canal());
        }

        @Test
        @DisplayName("Deve gerar token para ADMIN_GERAL sem estabelecimentoId")
        void deveGerarTokenAdminGeral() {
            UsuarioAutenticadoResponse admin = adminGeral();
            String token = servico.gerarTokenAcesso(admin, CanalAcesso.BACKOFFICE);

            UsuarioLogado logado = servico.validarTokenAcesso(token);

            assertNull(logado.estabelecimentoId());
            assertEquals(PerfilUsuario.ADMIN_GERAL, logado.perfil());
        }

        @Test
        @DisplayName("Deve recusar token de acesso expirado")
        void deveRecusarTokenExpirado() {
            UsuarioAutenticadoResponse usuario = operador();
            String token = servico.gerarTokenAcesso(usuario, CanalAcesso.BACKOFFICE);

            // Avanca o relogio para alem da validade (3h + 1s)
            Clock futuro = Clock.fixed(
                    Instant.parse("2026-11-10T12:00:00Z").plus(ACCESS_TTL).plusSeconds(1),
                    ZoneOffset.UTC);
            JwtProperties props = new JwtProperties(SEGREDO, ACCESS_TTL, REFRESH_TTL, "agilizadelivery");
            ServicoJwt servicoFuturo = new ServicoJwt(props, futuro);

            assertThrows(TokenInvalidoException.class, () -> servicoFuturo.validarTokenAcesso(token));
        }

        @Test
        @DisplayName("Deve recusar token adulterado (assinatura invalida)")
        void deveRecusarTokenAdulterado() {
            UsuarioAutenticadoResponse usuario = operador();
            String token = servico.gerarTokenAcesso(usuario, CanalAcesso.BACKOFFICE);

            // Substitui a assinatura inteira (parte apos o ultimo ponto)
            int ultimoPonto = token.lastIndexOf('.');
            String adulterado = token.substring(0, ultimoPonto + 1) + "assinaturaAdulterada";

            assertThrows(TokenInvalidoException.class, () -> servico.validarTokenAcesso(adulterado));
        }

        @Test
        @DisplayName("Deve recusar token nulo ou vazio")
        void deveRecusarTokenNuloOuVazio() {
            assertThrows(TokenInvalidoException.class, () -> servico.validarTokenAcesso(null));
            assertThrows(TokenInvalidoException.class, () -> servico.validarTokenAcesso(""));
            assertThrows(TokenInvalidoException.class, () -> servico.validarTokenAcesso("   "));
        }

        @Test
        @DisplayName("Deve recusar refresh token usado como token de acesso")
        void deveRecusarRefreshComoAcesso() {
            UsuarioAutenticadoResponse usuario = operador();
            String refreshToken = servico.gerarRefreshToken(usuario.usuarioId(), CanalAcesso.BACKOFFICE);

            assertThrows(TokenInvalidoException.class, () -> servico.validarTokenAcesso(refreshToken));
        }
    }

    @Nested
    @DisplayName("Refresh token")
    class RefreshToken {

        @Test
        @DisplayName("Deve gerar e validar refresh token com sub e canal")
        void deveGerarEValidarRefreshToken() {
            UUID usuarioId = UUID.randomUUID();
            String refresh = servico.gerarRefreshToken(usuarioId, CanalAcesso.APP);

            ServicoJwt.RefreshValido valido = servico.validarRefreshToken(refresh);

            assertEquals(usuarioId, valido.usuarioId());
            assertEquals(CanalAcesso.APP, valido.canal());
        }

        @Test
        @DisplayName("Deve recusar refresh token expirado (30 dias)")
        void deveRecusarRefreshExpirado() {
            UUID usuarioId = UUID.randomUUID();
            String refresh = servico.gerarRefreshToken(usuarioId, CanalAcesso.BACKOFFICE);

            Clock futuro = Clock.fixed(
                    Instant.parse("2026-11-10T12:00:00Z").plus(REFRESH_TTL).plusSeconds(1),
                    ZoneOffset.UTC);
            JwtProperties props = new JwtProperties(SEGREDO, ACCESS_TTL, REFRESH_TTL, "agilizadelivery");
            ServicoJwt servicoFuturo = new ServicoJwt(props, futuro);

            assertThrows(TokenInvalidoException.class, () -> servicoFuturo.validarRefreshToken(refresh));
        }

        @Test
        @DisplayName("Deve recusar token de acesso usado como refresh")
        void deveRecusarAcessoComoRefresh() {
            UsuarioAutenticadoResponse usuario = operador();
            String accessToken = servico.gerarTokenAcesso(usuario, CanalAcesso.BACKOFFICE);

            assertThrows(TokenInvalidoException.class, () -> servico.validarRefreshToken(accessToken));
        }

        @Test
        @DisplayName("Deve recusar refresh token adulterado")
        void deveRecusarRefreshAdulterado() {
            UUID usuarioId = UUID.randomUUID();
            String refresh = servico.gerarRefreshToken(usuarioId, CanalAcesso.BACKOFFICE);

            // Troca varios caracteres da assinatura (parte apos o ultimo ponto)
            int ultimoPonto = refresh.lastIndexOf('.');
            String adulterado = refresh.substring(0, ultimoPonto + 1) + "assinaturaAdulterada";

            assertThrows(TokenInvalidoException.class, () -> servico.validarRefreshToken(adulterado));
        }
    }

    @Nested
    @DisplayName("Configuracao")
    class Configuracao {

        @Test
        @DisplayName("Deve recusar segredo ausente")
        void deveRecusarSegredoAusente() {
            JwtProperties props = new JwtProperties(null, ACCESS_TTL, REFRESH_TTL, "agilizadelivery");
            assertThrows(IllegalStateException.class, () -> new ServicoJwt(props));
        }

        @Test
        @DisplayName("Deve recusar segredo curto demais (menos de 32 bytes)")
        void deveRecusarSegredoCurto() {
            String segredoCurto = Base64.getEncoder().encodeToString("curto".getBytes());
            JwtProperties props = new JwtProperties(segredoCurto, ACCESS_TTL, REFRESH_TTL, "agilizadelivery");
            assertThrows(IllegalStateException.class, () -> new ServicoJwt(props));
        }

        @Test
        @DisplayName("Deve expor a validade do token de acesso em segundos")
        void deveExporAccessTtlEmSegundos() {
            assertEquals(3 * 3600, servico.accessTtlSegundos());
        }
    }

    @Nested
    @DisplayName("UsuarioLogado")
    class UsuarioLogadoTests {

        @Test
        @DisplayName("estabelecimentoObrigatorio deve retornar o id quando presente")
        void deveRetornarEstabelecimentoQuandoPresente() {
            UsuarioAutenticadoResponse usuario = operador();
            String token = servico.gerarTokenAcesso(usuario, CanalAcesso.BACKOFFICE);
            UsuarioLogado logado = servico.validarTokenAcesso(token);

            assertNotNull(logado.estabelecimentoObrigatorio());
            assertEquals(usuario.estabelecimentoId(), logado.estabelecimentoObrigatorio());
        }

        @Test
        @DisplayName("estabelecimentoObrigatorio deve lancar excecao para ADMIN_GERAL")
        void deveLancarExcecaoParaAdminGeral() {
            UsuarioAutenticadoResponse admin = adminGeral();
            String token = servico.gerarTokenAcesso(admin, CanalAcesso.BACKOFFICE);
            UsuarioLogado logado = servico.validarTokenAcesso(token);

            assertThrows(br.com.agilizadelivery.exception.AcessoNegadoException.class,
                    logado::estabelecimentoObrigatorio);
        }
    }
}
