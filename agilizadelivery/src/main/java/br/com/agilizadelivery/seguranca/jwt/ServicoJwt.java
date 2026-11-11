package br.com.agilizadelivery.seguranca.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.exception.TokenInvalidoException;
import br.com.agilizadelivery.service.CanalAcesso;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Emissao e validacao dos tokens (HS256, jjwt).
 *
 * Token de acesso: claims sub, estabelecimentoId (ausente para
 * ADMIN_GERAL), perfil e canal (D002). Refresh token: sub e canal.
 * Ambos levam o claim "tipo" (ACESSO ou REFRESH), para que um nao seja
 * aceito no lugar do outro. PROVISORIO: o claim "tipo" e o refresh token
 * nao estao no DVP 3.1.
 *
 * Nenhum token e logado aqui nem em lugar nenhum (Constituicao 6.1).
 */
@Component
public class ServicoJwt {

    static final String CLAIM_ESTABELECIMENTO = "estabelecimentoId";
    static final String CLAIM_PERFIL = "perfil";
    static final String CLAIM_CANAL = "canal";
    static final String CLAIM_TIPO = "tipo";
    static final String TIPO_ACESSO = "ACESSO";
    static final String TIPO_REFRESH = "REFRESH";

    private static final int TAMANHO_MINIMO_CHAVE = 32;

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey chave;
    private final JwtParser parser;

    @Autowired
    public ServicoJwt(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public ServicoJwt(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.chave = chave(properties.segredo());
        this.parser = Jwts.parser()
                .verifyWith(chave)
                .requireIssuer(properties.emissor())
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    private static SecretKey chave(String segredo) {
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException(
                    "agiliza.jwt.segredo nao configurado: defina a variavel de ambiente JWT_SECRET (Base64, 32+ bytes).");
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(segredo.trim());
        } catch (DecodingException e) {
            throw new IllegalStateException("agiliza.jwt.segredo deve estar em Base64.");
        }
        if (bytes.length < TAMANHO_MINIMO_CHAVE) {
            throw new IllegalStateException("agiliza.jwt.segredo deve ter ao menos 32 bytes (HS256).");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String gerarTokenAcesso(UsuarioAutenticadoResponse usuario, CanalAcesso canal) {
        Instant agora = clock.instant();
        return Jwts.builder()
                .issuer(properties.emissor())
                .subject(usuario.usuarioId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(properties.accessTtl())))
                .claim(CLAIM_ESTABELECIMENTO,
                        usuario.estabelecimentoId() == null ? null : usuario.estabelecimentoId().toString())
                .claim(CLAIM_PERFIL, usuario.perfil().name())
                .claim(CLAIM_CANAL, canal.name())
                .claim(CLAIM_TIPO, TIPO_ACESSO)
                .signWith(chave)
                .compact();
    }

    public String gerarRefreshToken(UUID usuarioId, CanalAcesso canal) {
        Instant agora = clock.instant();
        return Jwts.builder()
                .issuer(properties.emissor())
                .subject(usuarioId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(properties.refreshTtl())))
                .claim(CLAIM_CANAL, canal.name())
                .claim(CLAIM_TIPO, TIPO_REFRESH)
                .signWith(chave)
                .compact();
    }

    /** @throws TokenInvalidoException para qualquer token que nao seja de acesso valido. */
    public UsuarioLogado validarTokenAcesso(String token) {
        Claims claims = validar(token, TIPO_ACESSO);
        try {
            String estabelecimento = claims.get(CLAIM_ESTABELECIMENTO, String.class);
            PerfilUsuario perfil = PerfilUsuario.valueOf(claims.get(CLAIM_PERFIL, String.class));
            UUID estabelecimentoId = estabelecimento == null ? null : UUID.fromString(estabelecimento);
            if (estabelecimentoId == null && perfil != PerfilUsuario.ADMIN_GERAL) {
                throw new TokenInvalidoException();
            }
            return new UsuarioLogado(
                    UUID.fromString(claims.getSubject()),
                    estabelecimentoId,
                    perfil,
                    CanalAcesso.valueOf(claims.get(CLAIM_CANAL, String.class)));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new TokenInvalidoException();
        }
    }

    /** Devolve o usuario e o canal do refresh token; os demais dados vem do banco. */
    public RefreshValido validarRefreshToken(String token) {
        Claims claims = validar(token, TIPO_REFRESH);
        try {
            return new RefreshValido(
                    UUID.fromString(claims.getSubject()),
                    CanalAcesso.valueOf(claims.get(CLAIM_CANAL, String.class)));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new TokenInvalidoException();
        }
    }

    public long accessTtlSegundos() {
        return properties.accessTtl().toSeconds();
    }

    private Claims validar(String token, String tipoEsperado) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException();
        }
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token.trim()).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidoException();
        }
        if (!tipoEsperado.equals(claims.get(CLAIM_TIPO, String.class))) {
            throw new TokenInvalidoException();
        }
        return claims;
    }

    public record RefreshValido(UUID usuarioId, CanalAcesso canal) {
    }
}
